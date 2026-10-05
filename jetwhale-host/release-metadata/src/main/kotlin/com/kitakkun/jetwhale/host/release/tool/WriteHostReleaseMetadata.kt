package com.kitakkun.jetwhale.host.release.tool

import androidx.annotation.VisibleForTesting
import com.kitakkun.jetwhale.host.release.HostJarCheck
import com.kitakkun.jetwhale.host.release.HostPlatformRelease
import com.kitakkun.jetwhale.host.release.HostReleaseMetadata
import com.kitakkun.jetwhale.host.release.HostReleaseMetadataReader
import com.kitakkun.jetwhale.host.release.HostReleaseMetadataResult
import com.kitakkun.jetwhale.host.release.HostRuntimeRequirements
import com.kitakkun.jetwhale.host.release.HostVersion
import com.kitakkun.jetwhale.host.release.LauncherContract
import com.kitakkun.jetwhale.host.release.ReleaseMetadataSignatureVerifier
import com.kitakkun.jetwhale.host.release.check
import com.kitakkun.jetwhale.host.release.isAllowedHostJvmArgument
import com.kitakkun.jetwhale.host.release.sha256Hex
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

private const val RELEASE_DOWNLOAD_URL = "https://github.com/kitakkun/JetWhale/releases/download"

/**
 * Writes a release's `jetwhale-host-<version>.json` for the host jars it is given, then reads the
 * file back through the launcher's own reader and checks every jar against it.
 *
 * ```
 * --output <file> --version <tag> --main-class <class> --java-feature-version <n>
 * --module <name>…  --jvm-arg <argument>…  --platform-jvm-arg <os-arch>=<argument>…
 * --jar <os-arch>=<path>…
 * ```
 *
 * Each `--jar` becomes a platform entry whose URL is the `jetwhale-host-<version>-<os-arch>.jar` asset
 * of the `<version>` release, whatever the given file is called. Exits with 1 and the problems on
 * stderr when anything is missing, refused or does not verify.
 */
fun main(args: Array<String>) {
    val problems = writeHostReleaseMetadata(args.toList())
    if (problems.isNotEmpty()) {
        problems.forEach(System.err::println)
        exitProcess(1)
    }
}

/** Returns the problems that kept the metadata from being written or from verifying; none on success. */
@VisibleForTesting
internal fun writeHostReleaseMetadata(arguments: List<String>): List<String> {
    val request = try {
        HostReleaseMetadataRequest.parse(arguments)
    } catch (e: IllegalArgumentException) {
        return listOf(e.message.orEmpty())
    }

    val version = HostVersion.parse(request.versionName)
    val problems = buildList {
        if (version == null) add("${request.versionName} is not a release version")
        (request.jvmArgs + request.platformJvmArgs.values.flatten())
            .filterNot(::isAllowedHostJvmArgument)
            .forEach { add("$it is not a JVM argument the launcher contract allows") }
        request.jars.forEach { (platformKey, jar) ->
            if (!Files.isRegularFile(jar)) add("No $platformKey jar at $jar")
        }
        (request.platformJvmArgs.keys - request.jars.keys).forEach { add("$it has JVM arguments but no --jar") }
    }
    if (version == null || problems.isNotEmpty()) return problems

    val metadata = HostReleaseMetadata(
        format = HostReleaseMetadata.FORMAT,
        version = version,
        mainClass = request.mainClass,
        launcherContract = LauncherContract.VERSION,
        runtime = HostRuntimeRequirements(
            javaFeatureVersion = request.javaFeatureVersion,
            modules = request.modules,
        ),
        jvmArgs = request.jvmArgs,
        platforms = request.jars.mapValues { (platformKey, jar) ->
            HostPlatformRelease(
                url = "$RELEASE_DOWNLOAD_URL/${version.name}/jetwhale-host-${version.name}-$platformKey.jar",
                size = Files.size(jar),
                sha256 = sha256Hex(jar),
                jvmArgs = request.platformJvmArgs[platformKey].orEmpty(),
            )
        },
    )
    request.output.parent?.let(Files::createDirectories)
    Files.writeString(request.output, metadata.encode() + "\n")

    val reader = HostReleaseMetadataReader(ReleaseMetadataSignatureVerifier.JetWhaleReleases)
    return when (val read = reader.read(Files.readAllBytes(request.output), null)) {
        is HostReleaseMetadataResult.Read -> request.jars.mapNotNull { (platformKey, jar) ->
            val check = read.metadata.platforms.getValue(platformKey).check(jar)
            if (check == HostJarCheck.Matches) null else "$platformKey jar does not verify: $check"
        }

        else -> listOf("${request.output} does not read back: $read")
    }
}

private class HostReleaseMetadataRequest(
    val output: Path,
    val versionName: String,
    val mainClass: String,
    val javaFeatureVersion: Int,
    val modules: List<String>,
    val jvmArgs: List<String>,
    val platformJvmArgs: Map<String, List<String>>,
    val jars: Map<String, Path>,
) {
    companion object {
        fun parse(arguments: List<String>): HostReleaseMetadataRequest {
            val values = mutableMapOf<String, MutableList<String>>()
            val iterator = arguments.iterator()
            while (iterator.hasNext()) {
                val option = iterator.next()
                require(option in OPTIONS) { "Unknown option $option" }
                require(iterator.hasNext()) { "Expected a value after $option" }
                values.getOrPut(option) { mutableListOf() } += iterator.next()
            }

            fun single(option: String): String {
                val given = values[option].orEmpty()
                require(given.size == 1) { "Expected $option exactly once" }
                return given.single()
            }

            fun keyed(option: String): List<Pair<String, String>> = values[option].orEmpty().map { value ->
                val key = value.substringBefore('=', missingDelimiterValue = "")
                require(key.isNotEmpty()) { "Expected <os-arch>=<value> after $option, but was $value" }
                key to value.substringAfter('=')
            }

            val jars = keyed("--jar")
            require(jars.isNotEmpty()) { "Expected at least one --jar" }
            require(jars.map { it.first }.toSet().size == jars.size) { "Expected one --jar per platform" }
            return HostReleaseMetadataRequest(
                output = Path.of(single("--output")),
                versionName = single("--version"),
                mainClass = single("--main-class"),
                javaFeatureVersion = single("--java-feature-version").toIntOrNull()
                    ?: throw IllegalArgumentException("Expected a number after --java-feature-version"),
                modules = values["--module"].orEmpty(),
                jvmArgs = values["--jvm-arg"].orEmpty(),
                platformJvmArgs = keyed("--platform-jvm-arg").groupBy({ it.first }, { it.second }),
                jars = jars.associate { (platformKey, path) -> platformKey to Path.of(path) },
            )
        }

        private val OPTIONS = setOf(
            "--output",
            "--version",
            "--main-class",
            "--java-feature-version",
            "--module",
            "--jvm-arg",
            "--platform-jvm-arg",
            "--jar",
        )
    }
}
