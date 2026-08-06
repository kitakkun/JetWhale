package util

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask

/**
 * Gate run before publishing: asserts the committed lock file describes a releasable state, so a
 * stale or hand-edited file cannot make the BOM and the published POMs promise coordinates that
 * this release never uploads.
 *
 * Deliberately offline and git-free — it only needs the lock file and the module graph, so it works
 * on a shallow CI checkout.
 */
@UntrackedTask(because = "Validates source-tree state that carries no task output")
abstract class VerifyReleasePlanTask : DefaultTask() {
    @get:Internal
    abstract val modules: ListProperty<PublishModule>

    @get:Internal
    abstract val trainVersion: Property<String>

    @get:Internal
    abstract val alwaysPublish: SetProperty<String>

    @get:Internal
    abstract val lockFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val train = trainVersion.get()
        check(!train.endsWith("-SNAPSHOT")) { "$name works on release versions; drop -PjetwhaleSnapshot." }

        val published = PublishedVersions.parse(lockFile.get().asFile.readText())
        val problems = ReleasePlanner.verify(
            modules = modules.get(),
            trainVersion = train,
            published = published,
            alwaysPublishArtifactIds = alwaysPublish.get(),
        )
        if (problems.isNotEmpty()) {
            error(problems.joinToString(prefix = "${PublishedVersions.RELATIVE_PATH} is not ready to publish:\n  - ", separator = "\n  - "))
        }

        val republished = published.versions.filterValues { it == train }.keys
        logger.lifecycle("$train publishes ${republished.size} of ${published.versions.size} artifacts: ${republished.sorted().joinToString()}")
    }
}
