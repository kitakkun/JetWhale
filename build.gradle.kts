plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
    alias(libs.plugins.multiplatform) apply false
    alias(libs.plugins.serialization) apply false
}

allprojects {
    group = "com.kitakkun.jetwhale"
    version = rootProject.libs.versions.jetwhale.get()
}
