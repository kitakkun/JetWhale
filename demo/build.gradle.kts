plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.androidApplication)
}

kotlin {
    jvm()
    jvmToolchain(17)
    androidTarget()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.material3)
            implementation(compose.runtime)
            implementation(projects.jetwhaleAgentRuntime)
            implementation(projects.jetwhaleAgentSdk)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }

        androidMain.dependencies {
            implementation(libs.androidxActivityCompose)
            implementation(libs.androidxActivityKtx)
            implementation(libs.androidxCoreKtx)
        }
    }
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

compose.desktop {
    application {
        mainClass = "com.kitakkun.jetwhale.demo.MainKt"
    }
}
