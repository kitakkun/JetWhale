import org.gradle.process.CommandLineArgumentProvider

plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.metro)
    alias(libs.plugins.aboutLibraries)
}

val generateBuildConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/buildconfig")
    val version = libs.versions.jetwhale.get()

    outputs.dir(outputDir)

    doLast {
        val file = outputDir.get().file("com/kitakkun/jetwhale/host/BuildConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package com.kitakkun.jetwhale.host
            |
            |object BuildConfig {
            |    const val VERSION: String = "$version"
            |}
            """.trimMargin(),
        )
    }
}

compose.desktop {
    application {
        mainClass = "com.kitakkun.jetwhale.host.MainKt"
    }
}

val hostReleaseMetadataWriter: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    hostReleaseMetadataWriter(projects.jetwhaleHost.releaseMetadata)
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
            buildList {
                add("--output")
                add(dir.resolve("jetwhale-host-$version.json").path)
                add("--version")
                add(version)
                add("--main-class")
                add(checkNotNull(hostMainClass))
                add("--java-feature-version")
                add(JetWhaleHostRuntime.JAVA_FEATURE_VERSION.toString())
                JetWhaleHostRuntime.modules.forEach {
                    add("--module")
                    add(it)
                }
                JetWhaleHostRuntime.jvmArgs.forEach {
                    add("--jvm-arg")
                    add(it)
                }
                JetWhaleHostRuntime.platformJvmArgs.forEach { (platform, arguments) ->
                    arguments.forEach {
                        add("--platform-jvm-arg")
                        add("$platform=$it")
                    }
                    add("--jar")
                    add("$platform=${dir.resolve("jetwhale-host-$version-$platform.jar").path}")
                }
            }
        },
    )
}

val currentPlatformKey: Provider<String> = providers.systemProperty("os.name")
    .zip(providers.systemProperty("os.arch")) { osName, osArch ->
        val os = when {
            osName.startsWith("Mac") -> "macos"
            osName.startsWith("Windows") -> "windows"
            else -> "linux"
        }
        val arch = if (osArch == "aarch64" || osArch == "arm64") "arm64" else "x64"
        "$os-$arch"
    }
val uberJar: FileCollection = files(tasks.matching { it.name == "packageUberJarForCurrentOS" })

val writeBundledHostMetadata = tasks.register<JavaExec>("writeBundledHostMetadata") {
    classpath = hostReleaseMetadataWriter
    mainClass.set("com.kitakkun.jetwhale.host.release.tool.WriteHostReleaseMetadataKt")

    val metadataFile = layout.buildDirectory.file("bundled-host-metadata/release.json")
    val hostVersion = libs.versions.jetwhale.get()
    val hostMainClass = compose.desktop.application.mainClass
    val platformKey = currentPlatformKey
    val hostJar = uberJar
    inputs.files(hostJar)
    outputs.file(metadataFile)
    argumentProviders.add(
        CommandLineArgumentProvider {
            buildList {
                add("--output")
                add(metadataFile.get().asFile.path)
                add("--version")
                add(hostVersion)
                add("--main-class")
                add(checkNotNull(hostMainClass))
                add("--java-feature-version")
                add(JetWhaleHostRuntime.JAVA_FEATURE_VERSION.toString())
                JetWhaleHostRuntime.modules.forEach {
                    add("--module")
                    add(it)
                }
                JetWhaleHostRuntime.jvmArgs.forEach {
                    add("--jvm-arg")
                    add(it)
                }
                JetWhaleHostRuntime.platformJvmArgs[platformKey.get()].orEmpty().forEach {
                    add("--platform-jvm-arg")
                    add("${platformKey.get()}=$it")
                }
                add("--jar")
                add("${platformKey.get()}=${hostJar.singleFile.path}")
            }
        },
    )
}

val bundledHostDirectory = layout.buildDirectory.dir("bundled-host")
val bundledHost = tasks.register<Sync>("bundledHost") {
    description = "Collects the host jar and release metadata that the launcher's package carries."

    val hostVersion = libs.versions.jetwhale.get()
    val hostJarName = currentPlatformKey.map { "jetwhale-host-$hostVersion-$it.jar" }
    val hostJar = uberJar
    from(hostJar) { rename { hostJarName.get() } }
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

// Merging signed dependency jars (e.g. BouncyCastle) into an uber jar invalidates their
// signatures; leftover META-INF signature files then make the JVM reject the jar at launch
// with "Invalid signature file digest for Manifest main attributes".
tasks.withType<org.gradle.jvm.tasks.Jar>().matching { it.name.contains("UberJar") }.configureEach {
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

compose.resources {
    packageOfResClass = "com.kitakkun.jetwhale.host"
}

tasks.register<JavaExec>("runHeadless") {
    group = "application"
    description = "Runs the JetWhale host with no GUI window — agent WebSocket server, MCP server, plugins and adb auto-wiring only."

    mainClass.set("com.kitakkun.jetwhale.host.MainKt")
    classpath = sourceSets.main.get().runtimeClasspath

    // An argument provider rather than `args`, because `--args` on the command line replaces `args`
    // and would drop the flag that selects this mode.
    argumentProviders.add(CommandLineArgumentProvider { listOf("--headless") })

    val appDataDir = providers.gradleProperty("jetwhaleAppDataDir")
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            appDataDir.map { listOf("-Djetwhale.appDataDir=$it") }.getOrElse(emptyList())
        },
    )
}

val aboutLibrariesDir = layout.buildDirectory.dir("generated/aboutlibraries")

kotlin {
    jvmToolchain(JetWhaleHostRuntime.JAVA_FEATURE_VERSION)

    compilerOptions {
        freeCompilerArgs.add("-opt-in=soil.query.annotation.ExperimentalSoilQueryApi")
        freeCompilerArgs.add("-opt-in=com.kitakkun.jetwhale.annotations.InternalJetWhaleApi")
    }

    sourceSets {
        main {
            kotlin.srcDir(generateBuildConfig.map { it.outputs.files })
            resources.srcDir(aboutLibrariesDir)
        }
    }
}

dependencies {
    implementation(projects.jetwhaleHostSdk)
    implementation(projects.jetwhaleProtocol.core)
    implementation(projects.jetwhaleHost.feature.settings)
    implementation(projects.jetwhaleHost.feature.plugin)
    implementation(projects.jetwhaleHostUi)
    implementation(projects.jetwhaleHost.core.model)
    implementation(projects.jetwhaleHost.core.data)
    implementation(libs.logbackClassic)
    implementation(projects.jetwhaleHost.core.mcp)
    implementation(projects.jetwhaleHost.core.architecture)
    implementation(projects.jetwhaleHost.core.ui)
    implementation(projects.jetwhaleHost.releaseMetadata)

    implementation(libs.bundles.navigation3)
    implementation(libs.kotlinxSerializationJson)
    implementation(libs.kotlinxCollectionsImmutable)
    implementation(libs.soilQueryCompose)
    implementation(libs.androidxDatastorePreferences)
    implementation(libs.material3)
    implementation(libs.aboutLibrariesCore)

    implementation(libs.jetbrainsComposeMaterialIconsExtended)
    compileOnly(libs.androidxAnnotation)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.jetbrainsComposeUiTestJUnit4)
}

aboutLibraries {
    export {
        outputFile = aboutLibrariesDir.get().file("licenses.json")
    }
    collect {
        this.configPath = file("aboutlibraries")
    }
}

// The export writes into a resource directory registered above as a plain path, so Gradle cannot
// tell the export has to run first; without this, the packaged licenses.json is missing or stale.
tasks.named("copyNonXmlValueResourcesForMain") {
    dependsOn("exportLibraryDefinitions")
}
