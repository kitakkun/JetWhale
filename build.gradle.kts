plugins {
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.multiplatform) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.debuggerComposeFeature) apply false
    alias(libs.plugins.metro) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.mokkery) apply false
    alias(libs.plugins.aboutLibraries) apply false
    alias(libs.plugins.mavenPublish) apply false
    alias(libs.plugins.publish) apply false
}

allprojects {
    group = "com.kitakkun.jetwhale"
    version = rootProject.libs.versions.jetwhale.get()
}
