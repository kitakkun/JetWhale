plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.compose)
}

dependencies {
    implementation(libs.soilQueryCompose)
    implementation(libs.soilReacty)
}
