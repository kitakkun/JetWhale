package util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val TRAIN = "1.0.0-alpha11"
private const val PREVIOUS = "1.0.0-alpha10"

/** annotations <- protocol <- sdk, plus a leaf that only depends on protocol. */
private val ANNOTATIONS = PublishModule(":annotations", "jetwhale-annotations", listOf("annotations"), emptyList())
private val PROTOCOL = PublishModule(":protocol", "jetwhale-protocol-core", listOf("protocol"), listOf(":annotations"))
private val SDK = PublishModule(":sdk", "jetwhale-agent-sdk", listOf("sdk"), listOf(":protocol"))
private val HOST_PLUGIN = PublishModule(":host", "jetwhale-network-inspector", listOf("host"), listOf(":protocol"))
private val MODULES = listOf(ANNOTATIONS, PROTOCOL, SDK, HOST_PLUGIN)

private val RECORDED = MODULES.associate { it.artifactId to PREVIOUS }

private fun plan(
    modules: List<PublishModule> = MODULES,
    recorded: Map<String, String> = RECORDED,
    globalChange: Boolean = false,
    dirty: Set<String> = emptySet(),
    alwaysPublish: Set<String> = emptySet(),
) = ReleasePlanner.compute(
    modules = modules,
    trainVersion = TRAIN,
    currentVersions = recorded,
    globalChange = globalChange,
    dirtyModuleIds = dirty,
    alwaysPublishArtifactIds = alwaysPublish,
)

class ReleasePlannerTest {
    @Test
    fun `an unchanged artifact keeps the version it was last published under`() {
        val plan = plan(dirty = setOf(":sdk"))

        assertEquals(TRAIN, plan.versions.getValue("jetwhale-agent-sdk"))
        assertEquals(PREVIOUS, plan.versions.getValue("jetwhale-annotations"))
        assertEquals(setOf("jetwhale-agent-sdk"), plan.republished)
    }

    @Test
    fun `a change propagates to every artifact downstream of it`() {
        // annotations moves, so protocol's POM has to name the new version, and so on to the leaves.
        val plan = plan(dirty = setOf(":annotations"))

        assertEquals(
            setOf(
                "jetwhale-annotations",
                "jetwhale-protocol-core",
                "jetwhale-agent-sdk",
                "jetwhale-network-inspector",
            ),
            plan.republished,
        )
    }

    @Test
    fun `a change does not propagate upstream`() {
        val plan = plan(dirty = setOf(":sdk"))

        assertEquals(PREVIOUS, plan.versions.getValue("jetwhale-protocol-core"))
        assertEquals(PREVIOUS, plan.versions.getValue("jetwhale-annotations"))
    }

    @Test
    fun `a global change republishes everything`() {
        val plan = plan(globalChange = true)

        assertEquals(MODULES.map { it.artifactId }.toSet(), plan.republished)
        assertTrue(plan.versions.values.all { it == TRAIN })
    }

    @Test
    fun `an always published artifact moves even when nothing changed`() {
        val plan = plan(alwaysPublish = setOf("jetwhale-network-inspector"))

        assertEquals(setOf("jetwhale-network-inspector"), plan.republished)
        assertEquals(PREVIOUS, plan.versions.getValue("jetwhale-protocol-core"))
    }

    @Test
    fun `an artifact that has never been published is republished`() {
        val newModule = PublishModule(":new", "jetwhale-new", listOf("new"), emptyList())

        val plan = plan(modules = MODULES + newModule)

        assertEquals(setOf("jetwhale-new"), plan.republished)
        assertEquals(TRAIN, plan.versions.getValue("jetwhale-new"))
    }
}

class VersionCatalogDiffTest {
    @Test
    fun `bumping only the train version is not a global change`() {
        val diff = """
            diff --git a/gradle/libs.versions.toml b/gradle/libs.versions.toml
            --- a/gradle/libs.versions.toml
            +++ b/gradle/libs.versions.toml
            @@ -1,3 +1,3 @@
             [versions]
            -jetwhale = "1.0.0-alpha10"
            +jetwhale = "1.0.0-alpha11"
        """.trimIndent()

        assertEquals(false, ReleasePlanner.versionCatalogDiffIsGlobal(diff))
    }

    @Test
    fun `bumping any other version is a global change`() {
        val diff = """
            --- a/gradle/libs.versions.toml
            +++ b/gradle/libs.versions.toml
            -jetwhale = "1.0.0-alpha10"
            +jetwhale = "1.0.0-alpha11"
            -kotlin = "2.4.10"
            +kotlin = "2.5.0"
        """.trimIndent()

        assertEquals(true, ReleasePlanner.versionCatalogDiffIsGlobal(diff))
    }

    @Test
    fun `an entry whose name merely starts with jetwhale is a global change`() {
        val diff = """
            --- a/gradle/libs.versions.toml
            +++ b/gradle/libs.versions.toml
            +jetwhaleSomethingElse = "1.0.0"
        """.trimIndent()

        assertEquals(true, ReleasePlanner.versionCatalogDiffIsGlobal(diff))
    }

    @Test
    fun `whitespace-only changes are not a global change`() {
        val diff = """
            --- a/gradle/libs.versions.toml
            +++ b/gradle/libs.versions.toml
            -
            +
        """.trimIndent()

        assertEquals(false, ReleasePlanner.versionCatalogDiffIsGlobal(diff))
    }

    @Test
    fun `an empty diff is not a global change`() {
        assertEquals(false, ReleasePlanner.versionCatalogDiffIsGlobal(""))
    }
}

class ReleasePlanVerificationTest {
    private fun verify(
        recorded: Map<String, String>,
        previousTrainVersion: String = TRAIN,
        alwaysPublish: Set<String> = emptySet(),
    ) = ReleasePlanner.verify(
        modules = MODULES,
        trainVersion = TRAIN,
        published = PublishedVersions(recorded, previousTrainVersion),
        alwaysPublishArtifactIds = alwaysPublish,
    )

    @Test
    fun `a lock file recorded for this release passes`() {
        val recorded = mapOf(
            "jetwhale-annotations" to PREVIOUS,
            "jetwhale-protocol-core" to PREVIOUS,
            "jetwhale-agent-sdk" to TRAIN,
            "jetwhale-network-inspector" to TRAIN,
        )

        assertEquals(emptyList(), verify(recorded, alwaysPublish = setOf("jetwhale-network-inspector")))
    }

    @Test
    fun `a lock file left at the previous release is rejected`() {
        val problems = verify(RECORDED, previousTrainVersion = PREVIOUS)

        assertTrue(problems.any { it.contains("previous.train.version") }, problems.toString())
    }

    @Test
    fun `an artifact missing from the lock file is rejected`() {
        val problems = verify(RECORDED - "jetwhale-agent-sdk")

        assertTrue(problems.any { it.contains("jetwhale-agent-sdk") && it.contains("no entry") }, problems.toString())
    }

    @Test
    fun `an always published artifact left behind is rejected`() {
        val problems = verify(RECORDED, alwaysPublish = setOf("jetwhale-network-inspector"))

        assertTrue(
            problems.any { it.contains("jetwhale-network-inspector") && it.contains("every release") },
            problems.toString(),
        )
    }

    @Test
    fun `an always published id that names no module is rejected`() {
        // Otherwise a typo — or an artifactId that was renamed out from under the set — silently
        // republishes nothing, which is the one thing the set exists to prevent.
        val problems = verify(RECORDED, alwaysPublish = setOf("jetwhale-gradle-plugin"))

        assertTrue(
            problems.any { it.contains("jetwhale-gradle-plugin") && it.contains("no module publishes it") },
            problems.toString(),
        )
    }

    @Test
    fun `a lock entry that names no module is rejected`() {
        val problems = verify(RECORDED + ("jetwhale-removed" to PREVIOUS))

        assertTrue(
            problems.any { it.contains("jetwhale-removed") && it.contains("no module publishes it") },
            problems.toString(),
        )
    }

    @Test
    fun `an artifact staying behind while its dependency moves is rejected`() {
        // The stale artifact's POM would keep naming the dependency's old version.
        val recorded = RECORDED + ("jetwhale-protocol-core" to TRAIN)

        val problems = verify(recorded)

        assertTrue(
            problems.any { it.contains("jetwhale-agent-sdk") && it.contains("jetwhale-protocol-core") },
            problems.toString(),
        )
    }
}

class PublishedVersionsTest {
    @Test
    fun `render and parse round-trip`() {
        val versions = mapOf("jetwhale-b" to TRAIN, "jetwhale-a" to PREVIOUS)

        val parsed = PublishedVersions.parse(PublishedVersions.render(versions, TRAIN))

        assertEquals(versions, parsed.versions)
        assertEquals(TRAIN, parsed.previousTrainVersion)
    }

    @Test
    fun `artifacts are rendered in a stable order`() {
        val rendered = PublishedVersions.render(mapOf("jetwhale-b" to TRAIN, "jetwhale-a" to TRAIN), TRAIN)

        val artifactLines = rendered.lines().filter { it.startsWith("artifact.") }
        assertEquals(listOf("artifact.jetwhale-a=$TRAIN", "artifact.jetwhale-b=$TRAIN"), artifactLines)
    }
}
