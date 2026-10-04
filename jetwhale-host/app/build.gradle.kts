import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.metro)
    alias(libs.plugins.aboutLibraries)
    alias(libs.plugins.conveyor)
}

// Conveyor packages require a purely numeric version. Map the pre-release suffix
// to a numeric 4th component so successive pre-releases are recognized as updates
// (e.g. 1.0.0-alpha08 -> 1.0.0.8). The final stable release must bump the base
// version (e.g. 1.0.1) so it sorts above its own pre-releases.
// Snapshot builds never go through Conveyor, so they keep the root convention's
// `-SNAPSHOT`-suffixed version instead of this numeric override.
if (!hasProperty("jetwhaleSnapshot")) {
    version = libs.versions.jetwhale.get().let { full ->
        val base = full.substringBefore("-")
        val preReleaseNumber = full.substringAfter("-", "").filter { it.isDigit() }.toIntOrNull()
        if (preReleaseNumber != null) "$base.$preReleaseNumber" else base
    }
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
        nativeDistributions {
            packageName = "JetWhale Debugger"
            copyright = "© 2026 kitakkun"
            // The DMG and MSI formats take only a numeric MAJOR.MINOR.PATCH, so the pre-release
            // suffix is dropped; the four-part Conveyor version above would not pass either.
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
            jvmArgs(
                "-Dapple.awt.application.appearance=system",
            )

            macOS {
                iconFile.set(file("src/main/resources/icon.icns"))
            }
            windows {
                iconFile.set(file("src/main/resources/icon.ico"))
            }
            linux {
                iconFile.set(file("src/main/resources/icon.png"))
                // Debian sorts `~` below everything, even the end of the string, so 1.0.0~alpha13 <
                // 1.0.0~alpha14 < 1.0.0 and apt upgrades from one pre-release to the next.
                debPackageVersion = libs.versions.jetwhale.get().replace('-', '~')
                menuGroup = "Development;Debugger;"
                appCategory = "devel"
            }
        }
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
    // 21 (not the repo-wide 17): app-runtime dependencies such as aboutlibraries 14+ ship Java 21
    // bytecode, and the Metro build plugins already require a 21 build JVM anyway. Published
    // SDK/agent artifacts stay on 17 for consumer compatibility. Vendor pin makes Conveyor bundle
    // a maintained Corretto build instead of the stale OpenJDK GA archive it would pick by default.
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.AMAZON)
    }

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

    // Resolved by Conveyor when cross-building packages for each platform. Written out because the
    // Compose plugin deprecated the `compose.desktop.<platform>` accessors; these are the
    // coordinates they resolved to.
    val composeDesktop = "org.jetbrains.compose.desktop:desktop-jvm"
    val composeVersion = libs.versions.jetbrainsCompose.get()
    linuxAmd64("$composeDesktop-linux-x64:$composeVersion")
    macAmd64("$composeDesktop-macos-x64:$composeVersion")
    macAarch64("$composeDesktop-macos-arm64:$composeVersion")
    windowsAmd64("$composeDesktop-windows-x64:$composeVersion")
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
