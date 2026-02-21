import util.implementation
import util.library
import util.libs

plugins {
    id("jvm")
    id("compose")
    id("dev.zacsweers.metro")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=soil.query.annotation.ExperimentalSoilQueryApi")
        freeCompilerArgs.add("-opt-in=com.kitakkun.jetwhale.annotations.InternalJetWhaleApi")
    }
}

dependencies {
    implementation(libs.library("soilQueryCompose"))
    implementation(libs.library("jetbrainsComposeMaterialIconsExtended"))
}
