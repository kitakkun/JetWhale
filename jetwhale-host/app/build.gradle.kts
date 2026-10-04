import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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
        jvmArgs("-Dapple.awt.application.appearance=system")
        nativeDistributions {
            packageName = "JetWhale Debugger"
            copyright = "© 2026 kitakkun"
            // The DMG and MSI formats take only a numeric MAJOR.MINOR.PATCH, so the pre-release
            // suffix is dropped.
            packageVersion = libs.versions.jetwhale.get().substringBefore("-")
            licenseFile = rootProject.rootDir.resolve("LICENSE")

            // The packaged app's runtime image lacks these modules unless they are listed, which
            // shows up only there as a NoClassDefFoundError.
            modules("jdk.unsupported")
            modules("java.naming")
            modules("java.sql")
            // java.lang.instrument.Instrumentation (ByteBuddy self-attach for plugin hot-reload)
            // lives in java.instrument; without it the packaged app crashes on startup.
            modules("java.instrument")

            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Msi,
                TargetFormat.Deb,
            )
            macOS {
                iconFile.set(file("src/main/resources/icon.icns"))
            }
            windows {
                iconFile.set(file("src/main/resources/icon.ico"))
            }
            linux {
                iconFile.set(file("src/main/resources/icon.png"))
            }
        }
    }
}

// The Compose Gradle plugin adds -Dcompose.application.configure.swing.globals=true, and
// -Xdock:name on macOS, to the packaged app's launcher by itself. A host jar the JetWhale launcher
// starts gets neither unless it is listed here.
val hostReleaseJvmArgs = listOf("-Dcompose.application.configure.swing.globals=true") + compose.desktop.application.jvmArgs
val hostReleasePlatforms = mapOf(
    "macos-arm64" to listOf("-Xdock:name=${compose.desktop.application.nativeDistributions.packageName}"),
    "linux-x64" to emptyList(),
    "windows-x64" to emptyList(),
)

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
    val runtimeModules = compose.desktop.application.nativeDistributions.modules.toList()
    val javaFeatureVersion = java.toolchain.languageVersion.map { it.asInt() }
    val hostJvmArgs = hostReleaseJvmArgs
    val releasePlatforms = hostReleasePlatforms
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
                add(javaFeatureVersion.get().toString())
                runtimeModules.forEach {
                    add("--module")
                    add(it)
                }
                hostJvmArgs.forEach {
                    add("--jvm-arg")
                    add(it)
                }
                releasePlatforms.forEach { (platform, arguments) ->
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
    // 21, not the repo-wide 17: app-only dependencies such as aboutlibraries-core ship Java 21
    // bytecode. Published SDK and agent modules stay on 17 for their consumers.
    jvmToolchain(21)

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
