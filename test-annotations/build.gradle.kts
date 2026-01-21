plugins {
    alias(libs.plugins.multiplatform)
}

kotlin {
    androidLibrary.namespace = "com.kitakkun.test.annotations"
}

dependencies {
    commonMainApi(libs.kotlinTest)
}
