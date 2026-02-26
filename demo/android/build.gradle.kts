plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "com.kitakkun.jetwhale.demo"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        targetSdk = 36
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(projects.demo.shared)
    implementation(libs.androidxActivityCompose)
    implementation(libs.androidxActivityKtx)
    implementation(libs.androidxCoreKtx)
}
