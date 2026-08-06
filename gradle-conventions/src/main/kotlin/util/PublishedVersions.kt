package util

import org.gradle.api.Project
import java.io.File
import java.util.Properties

/**
 * The version each published JetWhale artifact currently resolves to, recorded in
 * `gradle/published-versions.properties` at the repository root.
 *
 * A release republishes only the artifacts that changed, so an artifact stays at the release
 * ("train") version it was last published under. The `prepareRelease` task owns the file; the
 * `publish` convention, the BOM and the published version catalog only read it.
 *
 * The train version itself is not stored here — `gradle/libs.versions.toml`'s `jetwhale` entry is
 * its single source of truth.
 */
class PublishedVersions(
    /** artifactId to the version it currently resolves to. */
    val versions: Map<String, String>,
    /** Train version of the previous release; the tag change detection diffs against. */
    val previousTrainVersion: String,
) {
    /** The version [artifactId] currently resolves to, or `null` when it has never been published. */
    fun versionFor(artifactId: String): String? = versions[artifactId]

    companion object {
        const val RELATIVE_PATH = "gradle/published-versions.properties"

        private const val ARTIFACT_PREFIX = "artifact."
        private const val PREVIOUS_TRAIN_VERSION = "previous.train.version"

        /**
         * Locates the lock file by walking up from the build's root directory, so both the main
         * build and the `jetwhale-gradle-plugin` included build resolve the same file — mirroring
         * how their settings scripts share `../gradle/libs.versions.toml`.
         */
        fun locate(project: Project): File {
            var directory: File? = project.rootDir
            while (directory != null) {
                val candidate = File(directory, RELATIVE_PATH)
                if (candidate.isFile) return candidate
                directory = directory.parentFile
            }
            error("Could not find $RELATIVE_PATH at or above ${project.rootDir}")
        }

        /**
         * The version [artifactId] is published under in this build, falling back to the train
         * version for an artifact that has never been published.
         *
         * Snapshots ignore the recorded versions so every artifact shares one `-SNAPSHOT` version.
         */
        fun publishVersionFor(project: Project, artifactId: String): String {
            val trainVersion = project.version.toString()
            if (trainVersion.endsWith("-SNAPSHOT")) return trainVersion
            return load(project).versionFor(artifactId) ?: trainVersion
        }

        /** Every recorded artifact mapped to the version it is published under in this build. */
        fun publishVersions(project: Project): Map<String, String> {
            val trainVersion = project.version.toString()
            val recorded = load(project).versions
            return if (trainVersion.endsWith("-SNAPSHOT")) {
                recorded.mapValues { trainVersion }
            } else {
                recorded
            }
        }

        fun load(project: Project): PublishedVersions {
            // Read through the provider API so the file is registered as a configuration cache input.
            val text = project.providers
                .fileContents(project.objects.fileProperty().fileValue(locate(project)))
                .asText
                .get()
            return parse(text)
        }

        fun parse(text: String): PublishedVersions {
            val properties = Properties()
            text.reader().use { properties.load(it) }
            val previousTrainVersion = properties.getProperty(PREVIOUS_TRAIN_VERSION)
                ?: error("$RELATIVE_PATH is missing `$PREVIOUS_TRAIN_VERSION`")
            // Sorted, so generated BOM constraints and catalog entries keep a stable order rather
            // than the arbitrary one of Properties' hash set.
            val versions = properties.stringPropertyNames()
                .filter { it.startsWith(ARTIFACT_PREFIX) }
                .associate { it.removePrefix(ARTIFACT_PREFIX) to properties.getProperty(it) }
                .toSortedMap()
            return PublishedVersions(versions = versions, previousTrainVersion = previousTrainVersion)
        }

        fun render(versions: Map<String, String>, previousTrainVersion: String): String = buildString {
            appendLine("# The version each published artifact currently resolves to. A release only republishes the")
            appendLine("# artifacts that changed, so unchanged ones keep an older version and the BOM / published version")
            appendLine("# catalog map consumers onto the right combination.")
            appendLine("#")
            appendLine("# The release (\"train\") version itself lives in gradle/libs.versions.toml as `jetwhale`.")
            appendLine("#")
            appendLine("# Written by `./gradlew prepareRelease`. Do not edit by hand.")
            appendLine("$PREVIOUS_TRAIN_VERSION=$previousTrainVersion")
            appendLine()
            versions.toSortedMap().forEach { (artifactId, version) ->
                appendLine("$ARTIFACT_PREFIX$artifactId=$version")
            }
        }
    }
}
