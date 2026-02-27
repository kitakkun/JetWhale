plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.jetwhaleHostPlugin)
}

dependencies {
    ksp(libs.autoServiceKsp)
    implementation(libs.autoServiceAnnotations)

    compileOnly(projects.jetwhaleHostSdk)
    compileOnly(libs.material3)
    compileOnly(libs.kotlinxSerializationJson)
    api(projects.jetwhalePlugins.example.protocol)
}
