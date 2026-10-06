plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.serialization)
}

dependencies {
    compileOnly(libs.androidxAnnotation)
    testImplementation(libs.kotlinTest)
}
