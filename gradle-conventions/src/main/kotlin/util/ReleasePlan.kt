package util

import java.io.Serializable

/**
 * One publishable artifact, flattened to plain data so a release plan can be computed (and unit
 * tested) without touching Gradle state.
 */
data class PublishModule(
    /** Stable identity of the module; the Gradle project path for modules of the main build. */
    val id: String,
    val artifactId: String,
    /** Repository-root-relative paths whose changes make this module dirty. */
    val changeInputs: List<String>,
    /** Ids of the other publishable modules this one depends on. */
    val dependsOn: List<String>,
) : Serializable

/** What the next release publishes. */
internal data class ReleasePlan(
    /** artifactId to the version it resolves to after this release. */
    val versions: Map<String, String>,
    /** artifactIds that this release republishes at the train version. */
    val republished: Set<String>,
)

internal object ReleasePlanner {
    /**
     * Any change here alters the compiled output or the POM of every module — a Kotlin or Compose
     * bump changes klib metadata across the board — so it forces a full release.
     */
    val GLOBAL_CHANGE_INPUTS: List<String> = listOf(
        "build.gradle.kts",
        "settings.gradle.kts",
        "gradle.properties",
        "gradle-conventions",
        "gradle/wrapper",
    )

    private val TRAIN_VERSION_ENTRY = Regex("""^jetwhale\s*=""")

    fun compute(
        modules: List<PublishModule>,
        trainVersion: String,
        currentVersions: Map<String, String>,
        globalChange: Boolean,
        dirtyModuleIds: Set<String>,
        alwaysPublishArtifactIds: Set<String>,
    ): ReleasePlan {
        val republished = modules
            .filter { module ->
                globalChange ||
                    module.id in dirtyModuleIds ||
                    module.artifactId in alwaysPublishArtifactIds ||
                    module.artifactId !in currentVersions
            }
            .map { it.id }
            .toMutableSet()

        // A module whose published dependency moves needs republishing too, so its POM points at the
        // dependency's new version. Iterate to a fixpoint to reach the leaves of the graph.
        do {
            val added = modules
                .filter { it.id !in republished && it.dependsOn.any { dependency -> dependency in republished } }
                .map { it.id }
            republished += added
        } while (added.isNotEmpty())

        return ReleasePlan(
            versions = modules.associate { module ->
                module.artifactId to if (module.id in republished) {
                    trainVersion
                } else {
                    currentVersions.getValue(module.artifactId)
                }
            }.toSortedMap(),
            republished = modules.filter { it.id in republished }.map { it.artifactId }.toSortedSet(),
        )
    }

    /**
     * Whether a `git diff` of `gradle/libs.versions.toml` touches anything besides the train
     * version entry. Every release bumps `jetwhale`, so treating that line alone as a global change
     * would turn every release into a full release and defeat the whole scheme.
     */
    fun versionCatalogDiffIsGlobal(unifiedDiff: String): Boolean = unifiedDiff
        .lineSequence()
        .filter { (it.startsWith("+") || it.startsWith("-")) && !it.startsWith("+++") && !it.startsWith("---") }
        .map { it.drop(1).trim() }
        .filter { it.isNotEmpty() }
        .any { !TRAIN_VERSION_ENTRY.containsMatchIn(it) }

    /**
     * Consistency problems in a committed lock file, as human-readable messages. Empty when the file
     * describes a releasable state for [trainVersion].
     */
    fun verify(
        modules: List<PublishModule>,
        trainVersion: String,
        published: PublishedVersions,
        alwaysPublishArtifactIds: Set<String>,
    ): List<String> = buildList {
        if (published.previousTrainVersion != trainVersion) {
            add(
                "`previous.train.version` is ${published.previousTrainVersion} but the train version is " +
                    "$trainVersion. Run `./gradlew prepareRelease` and commit the result before tagging.",
            )
        }

        modules.filter { it.artifactId !in published.versions }.forEach {
            add("${it.artifactId} (${it.id}) has no entry. Run `./gradlew prepareRelease`.")
        }

        alwaysPublishArtifactIds
            .filter { published.versions[it] != null && published.versions[it] != trainVersion }
            .forEach { add("$it must be republished on every release but is recorded as ${published.versions[it]}.") }

        modules.filter { published.versions[it.artifactId].let { version -> version != null && version != trainVersion } }
            .forEach { module ->
                module.dependsOn
                    .mapNotNull { dependency -> modules.firstOrNull { it.id == dependency } }
                    .filter { published.versions[it.artifactId] == trainVersion }
                    .forEach { dependency ->
                        add(
                            "${module.artifactId} stays at ${published.versions[module.artifactId]} but depends on " +
                                "${dependency.artifactId}, which moves to $trainVersion. Run `./gradlew prepareRelease`.",
                        )
                    }
            }
    }
}
