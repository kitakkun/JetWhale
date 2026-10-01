plugins {
    alias(libs.plugins.multiplatform)
}

kotlin {
    android.namespace = "com.kitakkun.test.annotations"

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

dependencies {
    commonMainApi(libs.kotlinTest)
}
