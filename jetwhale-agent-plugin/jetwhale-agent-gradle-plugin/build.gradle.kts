import util.PublishedVersions

plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.publish)
}

group = "com.kitakkun.jetwhale"
version = libs.versions.jetwhale.get() + if (hasProperty("jetwhaleSnapshot")) "-SNAPSHOT" else ""

kotlin {
    // 17, matching gradle-conventions/jvm.gradle.kts — and not a matter of tidiness. Both of these
    // JARs are loaded by someone else's JVM: the Gradle daemon for the Gradle plugin, the Kotlin
    // compile daemon for the compiler plugin. Emitting Java 21 bytecode makes a consumer building on
    // JDK 17 fail with UnsupportedClassVersionError before any of this runs.
    jvmToolchain(17)
}

dependencies {
    // compileOnly: the consumer brings their own Kotlin Gradle plugin, and it must be theirs — this
    // is what lets one Gradle plugin serve every Kotlin version in the supported range.
    compileOnly(libs.kotlinGradlePlugin)

    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

/**
 * The compiler plugin's published version, as source.
 *
 * `getPluginArtifact()` has to name the compiler plugin's coordinates, and a hand-written literal
 * there is a version-skew bug waiting for the next release: publish 1.1.0 and the Gradle plugin
 * quietly keeps fetching the 1.0.0 compiler plugin. Taking it from the same recorded version that
 * publishes that artifact removes the opportunity — a release republishes only what changed, so the
 * two plugins do not always share a version.
 */
val generateVersionConstant by tasks.registering {
    val output = layout.buildDirectory.dir("generated/version/kotlin")
    val pluginVersion = PublishedVersions.publishVersionFor(project, "jetwhale-agent-compiler-plugin")
    inputs.property("version", pluginVersion)
    outputs.dir(output)
    doLast {
        val file = output.get().file("com/kitakkun/jetwhale/agent/gradle/JetWhaleAgentVersion.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package com.kitakkun.jetwhale.agent.gradle

            /** Generated from the compiler plugin's published version. Do not edit. */
            internal const val VERSION: String = "$pluginVersion"

            """.trimIndent(),
        )
    }
}

kotlin.sourceSets.named("main") {
    kotlin.srcDir(generateVersionConstant)
}

gradlePlugin {
    plugins {
        create("jetwhaleAgent") {
            id = "com.kitakkun.jetwhale.agent"
            displayName = "JetWhale Agent"
            description = "Bakes the build machine's address into JetWhale's buildMachineWss(port) endpoint."
            implementationClass = "com.kitakkun.jetwhale.agent.gradle.JetWhaleAgentSubplugin"
        }
    }
}

jetwhalePublish {
    artifactId = "jetwhale-agent-gradle-plugin"
    name = "JetWhale Agent Gradle Plugin"
    description = "Gradle plugin feeding the build machine's address to the JetWhale agent compiler plugin."
}
