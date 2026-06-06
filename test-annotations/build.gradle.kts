plugins {
    alias(libs.plugins.multiplatform)
}

kotlin {
    android.namespace = "com.kitakkun.test.annotations"
}

dependencies {
    commonMainApi(libs.kotlinTest)
}
