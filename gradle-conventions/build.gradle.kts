plugins {
    alias(libs.plugins.kotlinDsl)
}

dependencies {
    compileOnly(libs.bundles.gradlePlugins)

    testImplementation(libs.kotlinTest)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
