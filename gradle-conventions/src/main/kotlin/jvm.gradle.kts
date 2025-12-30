import org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension

plugins {
    id("org.jetbrains.kotlin.jvm")
}

configure<KotlinJvmExtension> {
    jvmToolchain(17)

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcontext-parameters",
        )
    }
}
