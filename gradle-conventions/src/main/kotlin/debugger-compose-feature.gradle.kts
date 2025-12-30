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
        freeCompilerArgs.add(
            "-opt-in=soil.query.annotation.ExperimentalSoilQueryApi",
        )
    }
}

dependencies {
    implementation(libs.library("soilQueryCompose"))
    implementation(compose.materialIconsExtended)
}
