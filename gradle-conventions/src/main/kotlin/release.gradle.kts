import util.PublishModule
import util.PublishedVersions
import util.ReleasePlanTask
import util.VerifyReleasePlanTask
import util.collectPublishModules
import util.registerAggregatePublishTask

// Root-build convention that owns the release plan: which artifacts a release republishes, recorded
// in gradle/published-versions.properties. Maven Central bills publishers on file count, so a
// release only uploads the artifacts whose sources actually moved.

/**
 * Artifacts that must exist at every release version regardless of whether they changed:
 *
 * - the BOM and the version catalog describe a release, so they belong to every release;
 * - the official plugins, because `OfficialPluginCatalog` builds their coordinates from the running
 *   host's version, and a missing artifact turns the host's one-click install into a 404;
 * - the QA agent, because `runJetWhaleQaAgent` defaults `qaAgentVersion` to `hostVersion`.
 */
val alwaysPublishArtifactIds = setOf(
    "jetwhale-bom",
    "jetwhale-catalog",
    "jetwhale-network-inspector",
    "jetwhale-nav3-navigator",
    "jetwhale-compose-semantics-inspector",
    "jetwhale-qa-agent",
)

/**
 * Artifacts of the `jetwhale-gradle-plugin` and `jetwhale-agent-plugin` included builds, which this
 * build cannot enumerate, so they are declared by hand. `sample` is deliberately not a change input:
 * it is a test consumer of the compiler plugin and is never published.
 */
val includedBuildModules = listOf(
    PublishModule(
        id = "includedBuild:jetwhale-host-gradle-plugin",
        artifactId = "jetwhale-host-gradle-plugin",
        changeInputs = listOf("jetwhale-gradle-plugin"),
        dependsOn = emptyList(),
    ),
    PublishModule(
        id = "includedBuild:jetwhale-agent-compiler-plugin",
        artifactId = "jetwhale-agent-compiler-plugin",
        changeInputs = listOf(
            "jetwhale-agent-plugin/jetwhale-agent-compiler-plugin",
            "jetwhale-agent-plugin/settings.gradle.kts",
        ),
        dependsOn = emptyList(),
    ),
    PublishModule(
        id = "includedBuild:jetwhale-agent-gradle-plugin",
        artifactId = "jetwhale-agent-gradle-plugin",
        changeInputs = listOf(
            "jetwhale-agent-plugin/jetwhale-agent-gradle-plugin",
            "jetwhale-agent-plugin/settings.gradle.kts",
        ),
        // Its generated VERSION constant names the compiler plugin's coordinates, so it has to be
        // republished whenever the compiler plugin moves.
        dependsOn = listOf("includedBuild:jetwhale-agent-compiler-plugin"),
    ),
)

val publishedVersionsFile = layout.projectDirectory.file(PublishedVersions.RELATIVE_PATH)

// The `publish` convention wires each module that is due for republishing into this task, so the
// vanniktech build service bundles exactly that set into a single Central Portal deployment. The
// convention registers it on demand; naming it here keeps it in `tasks` even for a build where
// nothing is due.
registerAggregatePublishTask(project)

val printReleasePlan = tasks.register<ReleasePlanTask>("printReleasePlan") {
    group = "publishing"
    description = "Reports which artifacts the next release would republish, without changing anything."
    mode.set(ReleasePlanTask.Mode.PRINT)
}

val prepareRelease = tasks.register<ReleasePlanTask>("prepareRelease") {
    group = "publishing"
    description = "Records the versions the next release publishes in ${PublishedVersions.RELATIVE_PATH}."
    mode.set(ReleasePlanTask.Mode.WRITE)
}

val verifyReleasePlan = tasks.register<VerifyReleasePlanTask>("verifyReleasePlan") {
    group = "publishing"
    description = "Fails when ${PublishedVersions.RELATIVE_PATH} does not describe a releasable state."
}

// projectsEvaluated so every jetwhalePublish { } block and project dependency has been declared.
gradle.projectsEvaluated {
    val publishModules = collectPublishModules(rootProject) + includedBuildModules
    val trainVersionValue = version.toString()

    listOf(printReleasePlan, prepareRelease).forEach { task ->
        task.configure {
            modules.set(publishModules)
            trainVersion.set(trainVersionValue)
            alwaysPublish.set(alwaysPublishArtifactIds)
            lockFile.set(publishedVersionsFile)
            repositoryRoot.set(layout.projectDirectory)
        }
    }

    verifyReleasePlan.configure {
        modules.set(publishModules)
        trainVersion.set(trainVersionValue)
        alwaysPublish.set(alwaysPublishArtifactIds)
        lockFile.set(publishedVersionsFile)
    }
}
