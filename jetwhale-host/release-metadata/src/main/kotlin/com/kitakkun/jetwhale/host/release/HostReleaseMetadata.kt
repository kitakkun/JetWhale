package com.kitakkun.jetwhale.host.release

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The `jetwhale-host-<version>.json` asset of a release: what the launcher needs to verify and start
 * that release's host jar.
 *
 * @property launcherContract The launcher contract the host relies on: the system properties it
 * reads, the JVM argument forms and the restart protocol. A launcher with a lower contract refuses
 * the release.
 * @property jvmArgs The arguments every platform gets, before the platform's own.
 * @property platforms Keyed by `os-arch` ([hostPlatformKey]).
 */
@Serializable
data class HostReleaseMetadata(
    val format: Int,
    val version: HostVersion,
    val mainClass: String,
    val launcherContract: Int,
    val runtime: HostRuntimeRequirements,
    val jvmArgs: List<String>,
    val platforms: Map<String, HostPlatformRelease>,
) {
    fun encode(): String = json.encodeToString(serializer(), this)

    fun jvmArgsFor(platformKey: String): List<String> = jvmArgs + platforms[platformKey]?.jvmArgs.orEmpty()

    /** Why a launcher with [launcher]'s capabilities cannot run this release, or null when it can. */
    fun refusalOn(launcher: LauncherCapabilities): HostReleaseRefusal? {
        if (launcherContract > launcher.contract) {
            return HostReleaseRefusal.NeedsNewerLauncher(launcherContract)
        }
        if (runtime.javaFeatureVersion > launcher.javaFeatureVersion) {
            return HostReleaseRefusal.NeedsNewerJava(runtime.javaFeatureVersion)
        }
        val missingModules = runtime.modules.filterNot(launcher.modules::contains)
        if (missingModules.isNotEmpty()) {
            return HostReleaseRefusal.MissingModules(missingModules)
        }
        if (launcher.platformKey !in platforms) {
            return HostReleaseRefusal.NoBuildForPlatform(launcher.platformKey)
        }
        val disallowedArgument = jvmArgsFor(launcher.platformKey).firstOrNull { !isAllowedHostJvmArgument(it) }
        if (disallowedArgument != null) {
            return HostReleaseRefusal.DisallowedJvmArgument(disallowedArgument)
        }
        return null
    }

    companion object {
        /** The metadata format this code reads and writes. A file with a higher one is refused. */
        const val FORMAT = 1

        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

        private val SHA256_HEX = Regex("[0-9a-f]{64}")

        fun assetName(version: HostVersion): String = "jetwhale-host-${version.name}.json"

        /**
         * Reads a metadata file. Unknown fields are ignored, so a later release can add some; a file
         * whose `format` is higher than [FORMAT] is refused instead of read with fields missing.
         *
         * It checks no signature, so it is only for the metadata that came inside the installed
         * package. A downloaded release's metadata is read through [HostReleaseMetadataReader].
         */
        fun decode(text: String): HostReleaseMetadataResult {
            val metadata = try {
                val format = json.decodeFromString(MetadataFormat.serializer(), text).format
                if (format < 1) return HostReleaseMetadataResult.Malformed("format $format is not a metadata format")
                if (format > FORMAT) return HostReleaseMetadataResult.NewerFormat(format)
                json.decodeFromString(serializer(), text)
            } catch (e: SerializationException) {
                return HostReleaseMetadataResult.Malformed(e.message.orEmpty())
            } catch (e: IllegalArgumentException) {
                return HostReleaseMetadataResult.Malformed(e.message.orEmpty())
            }
            metadata.platforms.forEach { (platformKey, release) ->
                if (!SHA256_HEX.matches(release.sha256)) {
                    return HostReleaseMetadataResult.Malformed("$platformKey has no lowercase hex SHA-256")
                }
                if (release.size < 0) {
                    return HostReleaseMetadataResult.Malformed("$platformKey has a negative size")
                }
            }
            return HostReleaseMetadataResult.Read(metadata)
        }
    }

    @Serializable
    private class MetadataFormat(val format: Int)
}

/**
 * @property javaFeatureVersion The lowest Java feature version the host runs on.
 * @property modules The modules the host needs from the runtime, Compose's defaults included.
 */
@Serializable
data class HostRuntimeRequirements(
    val javaFeatureVersion: Int,
    val modules: List<String>,
)

/**
 * One platform's host jar.
 *
 * @property url The release asset `downloadJetWhaleHost` also fetches.
 * @property sha256 Lowercase hex. It is what a jar is checked against before it is installed and
 * before every start; GitHub's own per-asset digest only describes whatever was uploaded.
 * @property jvmArgs The arguments this platform adds to [HostReleaseMetadata.jvmArgs].
 */
@Serializable
data class HostPlatformRelease(
    val url: String,
    val size: Long,
    val sha256: String,
    val jvmArgs: List<String>,
)

sealed interface HostReleaseMetadataResult {
    data class Read(val metadata: HostReleaseMetadata) : HostReleaseMetadataResult

    /** The signature check did not trust the file, so nothing in it was read. */
    data object Untrusted : HostReleaseMetadataResult

    data class NewerFormat(val format: Int) : HostReleaseMetadataResult

    data class Malformed(val reason: String) : HostReleaseMetadataResult
}
