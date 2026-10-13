import org.gradle.process.CommandLineArgumentProvider
import util.libs

plugins {
    id("org.jetbrains.compose")
}

/**
 * In-repo-only release plumbing for the host app, `:jetwhale-host:app`.
 *
 * Adds `checkHostReleaseVersion` and `writeHostReleaseMetadata`, which the release workflow runs, and
 * `bundledHost`, which collects this build's host uber jar with its `release.json`.
 * `:jetwhale-host:launcher` consumes that directory through `bundledHostElements` and packages it as
 * the host the installer carries.
 */

val hostReleaseMetadataWriter: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    hostReleaseMetadataWriter(project(":jetwhale-host:release-metadata"))
}

tasks.register<JavaExec>("checkHostReleaseVersion") {
    group = "distribution"
    description = "Fails unless -PhostReleaseVersion is a release version the launcher and the host can order."

    classpath = hostReleaseMetadataWriter
    mainClass.set("com.kitakkun.jetwhale.host.release.tool.CheckHostReleaseVersionKt")
    val releaseVersion = providers.gradleProperty("hostReleaseVersion")
    argumentProviders.add(CommandLineArgumentProvider { listOf(releaseVersion.get()) })
}

tasks.register<JavaExec>("writeHostReleaseMetadata") {
    group = "distribution"
    description = "Writes jetwhale-host-<version>.json for the host jars of every release platform in " +
        "-PhostReleaseDir, named jetwhale-host-<version>-<os-arch>.jar, with -PhostReleaseVersion as the version."

    classpath = hostReleaseMetadataWriter
    mainClass.set("com.kitakkun.jetwhale.host.release.tool.WriteHostReleaseMetadataKt")

    val rootDirectory = rootProject.layout.projectDirectory
    val releaseDir = providers.gradleProperty("hostReleaseDir").map { rootDirectory.dir(it).asFile }
    val releaseVersion = providers.gradleProperty("hostReleaseVersion")
    val hostMainClass = compose.desktop.application.mainClass
    argumentProviders.add(
        CommandLineArgumentProvider {
            val version = releaseVersion.get()
            val dir = releaseDir.get()
            listOf("--output", dir.resolve("jetwhale-host-$version.json").path) +
                JetWhaleHostRuntime.releaseMetadataArguments(version, checkNotNull(hostMainClass)) +
                JetWhaleHostRuntime.platformJvmArgs.keys.flatMap { platform ->
                    listOf("--jar", "$platform=${dir.resolve("jetwhale-host-$version-$platform.jar").path}")
                }
        },
    )
}

val uberJar: FileCollection = files(tasks.matching { it.name == "packageUberJarForCurrentOS" })

val writeBundledHostMetadata = tasks.register<JavaExec>("writeBundledHostMetadata") {
    classpath = hostReleaseMetadataWriter
    mainClass.set("com.kitakkun.jetwhale.host.release.tool.WriteHostReleaseMetadataKt")

    val metadataFile = layout.buildDirectory.file("bundled-host-metadata/release.json")
    val hostVersion = libs.findVersion("jetwhale").get().requiredVersion
    val hostMainClass = compose.desktop.application.mainClass
    val hostJar = uberJar
    inputs.files(hostJar)
    outputs.file(metadataFile)
    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf("--output", metadataFile.get().asFile.path) +
                JetWhaleHostRuntime.releaseMetadataArguments(hostVersion, checkNotNull(hostMainClass)) +
                listOf("--current-platform-jar", hostJar.singleFile.path)
        },
    )
}

val bundledHostDirectory = layout.buildDirectory.dir("bundled-host")
val bundledHost = tasks.register<Sync>("bundledHost") {
    description = "Collects the host jar and release metadata that the launcher's package carries."

    from(uberJar)
    from(writeBundledHostMetadata)
    into(bundledHostDirectory)
}

val bundledHostElements: Configuration by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add(bundledHostElements.name, bundledHostDirectory) {
        builtBy(bundledHost)
    }
}
