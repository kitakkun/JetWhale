plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.ksp)
    // Provides packagePlugin / installPlugin / runJetWhale tasks (see the jetwhale-plugin convention).
    alias(libs.plugins.jetwhalePlugin)
}

dependencies {
    ksp(libs.autoServiceKsp)
    implementation(libs.autoServiceAnnotations)

    compileOnly(projects.jetwhaleHostSdk)
    compileOnly(libs.material3)
    compileOnly(libs.kotlinxSerializationJson)
    api(projects.jetwhalePlugins.example.protocol)
}
