plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.publish)
}

group = "com.kitakkun.jetwhale"
version = libs.versions.jetwhale.get() + if (hasProperty("jetwhaleSnapshot")) "-SNAPSHOT" else ""

kotlin {
    jvmToolchain(21)
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
 * The plugin's own version, as source.
 *
 * `getPluginArtifact()` has to name the compiler plugin's coordinates, and a hand-written literal
 * there is a version-skew bug waiting for the next release: publish 1.1.0 and the Gradle plugin
 * quietly keeps fetching the 1.0.0 compiler plugin. Generating it from the same `version` that
 * publishes the artifact removes the opportunity.
 */
val generateVersionConstant by tasks.registering {
    val output = layout.buildDirectory.dir("generated/version/kotlin")
    val pluginVersion = version.toString()
    inputs.property("version", pluginVersion)
    outputs.dir(output)
    doLast {
        val file = output.get().file("com/kitakkun/jetwhale/agent/gradle/JetWhaleAgentVersion.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package com.kitakkun.jetwhale.agent.gradle

            /** Generated from the Gradle plugin's own version. Do not edit. */
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
