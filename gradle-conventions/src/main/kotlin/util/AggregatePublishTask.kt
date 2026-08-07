package util

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

/** Name of the per-build task that publishes exactly the artifacts this release moves. */
const val AGGREGATE_PUBLISH_TASK_NAME = "publishChangedToMavenCentral"

/**
 * The aggregate publish task of [rootProject]'s build, registering it on first use.
 *
 * Each build owns one: an included build's tasks are unreachable from the root build, so CI invokes
 * this task once per build. It always exists, even when nothing is due, so that invocation does not
 * fail on an unknown task name.
 */
internal fun registerAggregatePublishTask(rootProject: Project): TaskProvider<Task> = if (AGGREGATE_PUBLISH_TASK_NAME in rootProject.tasks.names) {
    rootProject.tasks.named(AGGREGATE_PUBLISH_TASK_NAME)
} else {
    rootProject.tasks.register(AGGREGATE_PUBLISH_TASK_NAME) {
        group = "publishing"
        description = "Publishes only the artifacts whose recorded version equals the current train version."
    }
}
