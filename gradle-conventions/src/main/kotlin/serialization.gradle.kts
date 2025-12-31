import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import util.commonMainImplementation
import util.implementation
import util.library
import util.libs

plugins {
    id("org.jetbrains.kotlin.plugin.serialization")
}

when (kotlinExtension) {
    is KotlinMultiplatformExtension -> {
        dependencies {
            commonMainImplementation(libs.library("kotlinxSerializationJson"))
        }
    }

    is KotlinSingleTargetExtension<*> -> {
        dependencies {
            implementation(libs.library("kotlinxSerializationJson"))
        }
    }
}
