package util

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Works out which artifacts the next release has to republish, by diffing each module against the
 * tag of the previous release.
 *
 * Everything happens in the task action so that shelling out to git never becomes a configuration
 * cache input; the task is untracked because it reads git state and writes into the source tree.
 */
@UntrackedTask(because = "Reads git history and writes into the source tree")
abstract class ReleasePlanTask : DefaultTask() {
    enum class Mode {
        /** Report the plan without touching the lock file. */
        PRINT,

        /** Write the plan into the lock file. */
        WRITE,
    }

    @get:Internal
    abstract val mode: Property<Mode>

    @get:Internal
    abstract val modules: ListProperty<PublishModule>

    @get:Internal
    abstract val trainVersion: Property<String>

    @get:Internal
    abstract val alwaysPublish: SetProperty<String>

    @get:Internal
    abstract val lockFile: RegularFileProperty

    @get:Internal
    abstract val repositoryRoot: DirectoryProperty

    @get:Inject
    protected abstract val exec: ExecOperations

    @TaskAction
    fun computePlan() {
        val allModules = modules.get()
        val train = trainVersion.get()
        val alwaysPublishArtifactIds = alwaysPublish.get()
        val file = lockFile.get().asFile
        val published = PublishedVersions.parse(file.readText())

        check(!train.endsWith("-SNAPSHOT")) { "$name works on release versions; drop -PjetwhaleSnapshot." }
        if (mode.get() == Mode.WRITE) {
            check(train != published.previousTrainVersion) {
                "The train version is still ${published.previousTrainVersion}. Bump `jetwhale` in " +
                    "gradle/libs.versions.toml before running $name."
            }
        }

        val previousTag = published.previousTrainVersion
        check(git("rev-parse", "--verify", "--quiet", "refs/tags/$previousTag^{commit}").isNotBlank()) {
            "No tag $previousTag, which ${PublishedVersions.RELATIVE_PATH} names as the previous release. " +
                "Either the tags are not fetched (`git fetch --tags`), or `previous.train.version` names a " +
                "version that was never released — it must be a release tag, not a -SNAPSHOT one."
        }

        val plan = ReleasePlanner.compute(
            modules = allModules,
            trainVersion = train,
            currentVersions = published.versions,
            globalChange = detectGlobalChange(previousTag),
            dirtyModuleIds = allModules.filter { changedSince(previousTag, it.changeInputs) }.map { it.id }.toSet(),
            alwaysPublishArtifactIds = alwaysPublishArtifactIds,
        )

        report(plan, published, train, previousTag, alwaysPublishArtifactIds)

        if (mode.get() == Mode.WRITE) {
            file.writeText(PublishedVersions.render(plan.versions, train))
            logger.lifecycle("")
            logger.lifecycle("Updated ${PublishedVersions.RELATIVE_PATH}. Review the diff, then commit and tag.")
        }
    }

    private fun detectGlobalChange(previousTag: String): Boolean {
        if (changedSince(previousTag, ReleasePlanner.GLOBAL_CHANGE_INPUTS)) {
            logger.lifecycle("Build configuration changed since $previousTag — every artifact is republished.")
            return true
        }
        val catalogDiff = git("diff", previousTag, "HEAD", "--", "gradle/libs.versions.toml")
        if (ReleasePlanner.versionCatalogDiffIsGlobal(catalogDiff)) {
            logger.lifecycle("gradle/libs.versions.toml changed beyond the train version — every artifact is republished.")
            return true
        }
        return false
    }

    private fun report(
        plan: ReleasePlan,
        published: PublishedVersions,
        train: String,
        previousTag: String,
        alwaysPublishArtifactIds: Set<String>,
    ) {
        logger.lifecycle("Release plan for $train (previous release: $previousTag)")
        logger.lifecycle("")
        plan.versions.forEach { (artifactId, version) ->
            if (artifactId in plan.republished) {
                val note = if (artifactId in alwaysPublishArtifactIds) "  (always republished)" else ""
                logger.lifecycle("  publish  $artifactId  ${published.versionFor(artifactId) ?: "(new)"} -> $version$note")
            } else {
                logger.lifecycle("  keep     $artifactId  $version")
            }
        }
        logger.lifecycle("")
        logger.lifecycle("${plan.republished.size} of ${plan.versions.size} artifacts republished.")
    }

    /** `git diff --quiet` exits 1 when the paths differ, 0 when they do not, and >1 on failure. */
    private fun changedSince(previousTag: String, paths: List<String>): Boolean {
        val result = exec.exec {
            commandLine(listOf("git", "diff", "--quiet", previousTag, "HEAD", "--") + paths)
            workingDir = repositoryRoot.get().asFile
            isIgnoreExitValue = true
            standardOutput = ByteArrayOutputStream()
            errorOutput = ByteArrayOutputStream()
        }
        return when (val exitValue = result.exitValue) {
            0 -> false
            1 -> true
            else -> error("`git diff` failed with exit code $exitValue for $paths")
        }
    }

    private fun git(vararg arguments: String): String {
        val output = ByteArrayOutputStream()
        exec.exec {
            commandLine(listOf("git") + arguments)
            workingDir = repositoryRoot.get().asFile
            isIgnoreExitValue = true
            standardOutput = output
            errorOutput = ByteArrayOutputStream()
        }
        return output.toString()
    }
}
