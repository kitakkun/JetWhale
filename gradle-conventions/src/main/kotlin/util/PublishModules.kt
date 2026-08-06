package util

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency

/**
 * Flattens every project of [root] that applies the `publish` convention into plain [PublishModule]
 * data. Call this once all projects are evaluated, so `jetwhalePublish.artifactId` and the project
 * dependency graph are final.
 */
internal fun collectPublishModules(root: Project): List<PublishModule> {
    val publishing = root.allprojects.filter { it.plugins.hasPlugin("publish") }
    val publishingPaths = publishing.map { it.path }.toSet()
    return publishing
        .map { project ->
            PublishModule(
                id = project.path,
                artifactId = project.extensions.getByType(JetWhalePublishExtension::class.java).artifactId,
                changeInputs = project.changeInputs(root),
                dependsOn = project.configurations
                    .filter { it.declaresRealDependencies }
                    .flatMap { it.dependencies }
                    .filterIsInstance<ProjectDependency>()
                    .map { it.path }
                    .filter { it in publishingPaths && it != project.path }
                    .distinct()
                    .sorted(),
            )
        }
        .sortedBy { it.id }
}

/**
 * Dependency-scope configurations hold what the build script declares, including every Kotlin
 * Multiplatform source set scope (`commonMainApi`, `appleMainImplementation`, ...).
 *
 * `swiftPMDependenciesForLockFilesMetadataClasspathDependencies` is a dependency scope too, but the
 * Kotlin Gradle plugin fills it with every project in the build, which would make each module look
 * as if it depended on all the others.
 */
private val Configuration.declaresRealDependencies: Boolean
    get() = !isCanBeResolved && !isCanBeConsumed && !name.startsWith("swiftPMDependenciesForLockFiles")

private fun Project.changeInputs(root: Project): List<String> {
    val inputs = mutableListOf(projectDir.relativeTo(root.projectDir).invariantSeparatorsPath)
    // An ancestor project's own build script configures this module too — :jetwhale-protocol applies
    // a `group` to its children — and it sits outside this module's directory. The root build script
    // is already a global change input, so stop before it.
    var ancestor = parent
    while (ancestor != null && ancestor != root) {
        if (ancestor.buildFile.isFile) {
            inputs += ancestor.buildFile.relativeTo(root.projectDir).invariantSeparatorsPath
        }
        ancestor = ancestor.parent
    }
    return inputs
}
