plugins {
    alias(libs.plugins.multiplatform)
}

kotlin {
    android.namespace = "com.kitakkun.test.annotations"

    // `Ignore` is an expect/actual *annotation*, and expect/actual classes are still Beta. The flag
    // is how JetBrains asks you to acknowledge that (KT-61573) rather than a way of hiding it, and it
    // is set here alone because this is the only module in the repo that declares one.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

dependencies {
    commonMainApi(libs.kotlinTest)
}
