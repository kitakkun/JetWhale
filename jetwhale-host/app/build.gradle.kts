import org.gradle.process.CommandLineArgumentProvider

plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.metro)
    alias(libs.plugins.aboutLibraries)
    alias(libs.plugins.jetwhaleHostRelease)
}

val generateBuildConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/buildconfig")
    val version = libs.versions.jetwhale.get()
    val testBuildReleaseSourceVariable = if (providers.gradleProperty("jetwhaleTestBuild").isPresent) "\"JETWHALE_RELEASE_SOURCE\"" else "null"

    inputs.property("version", version)
    inputs.property("testBuildReleaseSourceVariable", testBuildReleaseSourceVariable)
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
            |    val TEST_BUILD_RELEASE_SOURCE_VARIABLE: String? = $testBuildReleaseSourceVariable
            |}
            """.trimMargin(),
        )
    }
}

compose.desktop {
    application {
        mainClass = "com.kitakkun.jetwhale.host.MainKt"
        jvmArgs(*JetWhaleHostRuntime.jvmArgs.toTypedArray())
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
