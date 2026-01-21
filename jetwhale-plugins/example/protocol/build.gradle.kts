plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    androidLibrary.namespace = "com.kitakkun.jetwhale.plugins.example.protocol"
}

dependencies {
    commonMainImplementation(libs.kotlinxSerializationJson)
}
