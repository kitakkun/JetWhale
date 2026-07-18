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
version = libs.versions.jetwhale.get().let { full ->
    val base = full.substringBefore("-")
    val preReleaseNumber = full.substringAfter("-", "").filter { it.isDigit() }.toIntOrNull()
    if (preReleaseNumber != null) "$base.$preReleaseNumber" else base
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
            // Remove pre-release suffix for package version
            packageVersion = libs.versions.jetwhale.get().substringBefore("-")
            licenseFile = rootProject.rootDir.resolve("LICENSE")

            // Fix runtime NoClassDefFoundError which occurs only on packaged application
            modules("jdk.unsupported")
            modules("java.naming")
            modules("java.sql")

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
            }
        }
    }
}

compose.resources {
    packageOfResClass = "com.kitakkun.jetwhale.host"
}

val aboutLibrariesDir = layout.buildDirectory.dir("generated/aboutlibraries")

kotlin {
    // Vendor pin makes Conveyor bundle a maintained Corretto build
    // instead of the stale OpenJDK GA archive it would pick by default.
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
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
    implementation(projects.jetwhaleHost.core.model)
    implementation(projects.jetwhaleHost.core.data)
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
    testImplementation(libs.kotlinTest)

    // Machine-specific Compose runtime dependencies resolved by Conveyor
    // when cross-building packages for each target platform.
    linuxAmd64(compose.desktop.linux_x64)
    macAmd64(compose.desktop.macos_x64)
    macAarch64(compose.desktop.macos_arm64)
    windowsAmd64(compose.desktop.windows_x64)
}

aboutLibraries {
    export {
        outputFile = aboutLibrariesDir.get().file("licenses.json")
    }
    collect {
        this.configPath = file("aboutlibraries")
    }
}

// Ensure that library definitions are up to date before packaging resources
tasks.named("copyNonXmlValueResourcesForMain") {
    dependsOn("exportLibraryDefinitions")
}
