@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.publish)
}

kotlin {
    explicitApi()
    abiValidation()
}

group = "com.kitakkun.jetwhale.plugins.xctestrunner"

dependencies {
    compileOnly(libs.kotlinxCoroutinesCore)
    compileOnly(libs.kotlinxSerializationJson)
    implementation(libs.okhttp)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.kotlinxCoroutinesTest)
    testImplementation(libs.kotlinxSerializationJson)
    testImplementation(libs.okhttpMockwebserver)
}

val zipRunnerProject by tasks.registering(Zip::class) {
    from(layout.projectDirectory.dir("../runner")) {
        exclude("**/xcuserdata/**", "**/*.xcuserstate")
    }
    archiveFileName = "JetWhaleRunner.zip"
    destinationDirectory = layout.buildDirectory.dir("runner-project")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.processResources {
    from(zipRunnerProject) { into("com/kitakkun/jetwhale/plugins/xctestrunner") }
}

jetwhalePublish {
    artifactId = "jetwhale-ios-xctest-runner"
    name = "JetWhale iOS XCTest Runner"
    description = "Client for the XCTest runner that drives iOS simulators and devices for JetWhale host plugins."
}
