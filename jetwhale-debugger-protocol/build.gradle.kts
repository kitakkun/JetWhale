plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.serialization)
}

kotlin {
    androidLibrary.namespace = "com.kitakkun.jetwhale.debugger.protocol"
}
