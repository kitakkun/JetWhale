@file:OptIn(ExperimentalAbiValidation::class)

import com.kitakkun.kotrail.gradle.KotrailExtension
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.publish)
    alias(libs.plugins.roborazzi)
}

kotlin {
    jvmToolchain(17)
    explicitApi()

    abiValidation {
    }

    // A compilation of its own for the previews, so that they are compiled and rule-checked on
    // every build without reaching the JAR, the sources JAR, the publication or the ABI dump.
    // Associating it with `main` also lets a preview call an internal declaration.
    target.compilations.create("preview") {
        associateWith(target.compilations.getByName("main"))
    }
}

dependencies {
    // Every one of these is exposed in the public API (composable signatures take Modifier, Color,
    // TextStyle, ...), and every one is provided by the host at runtime: a plugin consumes this
    // module as compileOnly, exactly like jetwhale-host-sdk. Deliberately no Material: the
    // library is built on foundation alone, so its API does not move with Material's.
    api(compose.runtime)
    api(compose.foundation)
    api(compose.ui)
    api(projects.jetwhaleHostSdk)
    "previewImplementation"(compose.runtime)
    "previewImplementation"(compose.foundation)
    "previewImplementation"(compose.ui)
    "previewImplementation"(libs.jetbrainsComposePreview)
    testImplementation(libs.kotlinTest)
    testImplementation(compose.desktop.currentOs)
    testImplementation(libs.jetbrainsComposeUiTestJUnit4)
    testImplementation(libs.roborazziComposeDesktop)
}

// Screenshot tests of the component gallery. The images are not committed: CI renders main and
// the pull request on one runner and posts the diff on the PR (see screenshot-check.yml).
// Locally, `recordRoborazziJvm` writes them here to look at.
roborazzi {
    outputDir.set(file("screenshots"))
}

tasks.named("check") {
    dependsOn("compilePreviewKotlin")
}

configure<KotrailExtension> {
    compilation("main") { configFile = file("kotrail-main.yaml") }
    compilation("preview") { configFile = file("kotrail-preview.yaml") }
    test { configFile = file("kotrail-test.yaml") }
}

jetwhalePublish {
    artifactId = "jetwhale-host-ui"
    name = "JetWhale Host UI"
    description = "Theme and UI components shared by the JetWhale host and its plugins"
}
