import org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension
import util.implementation
import util.library
import util.libs

plugins {
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

configure<KotlinJvmExtension> {
    compilerOptions {
        freeCompilerArgs.addAll(
            listOf(
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi"
            ),
        )
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.preview)
    implementation(compose.components.resources)
    implementation(libs.library("material3"))
}
