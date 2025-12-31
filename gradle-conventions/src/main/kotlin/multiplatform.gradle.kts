import com.android.build.api.dsl.androidLibrary
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

configure<KotlinMultiplatformExtension> {
    jvm()
    jvmToolchain(17)
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    macosX64()
    macosArm64()
    mingwX64()
    linuxX64()
    linuxArm64()

    androidLibrary {
        compileSdk = 36
    }
}
