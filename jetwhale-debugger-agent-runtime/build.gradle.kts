plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.serialization)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
}

kotlin {
    androidLibrary {
        namespace = "com.kitakkun.jetwhale.debugger.agent.runtime"
        compileSdk = 36
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.jetwhaleDebuggerProtocol)
            implementation(projects.jetwhaleDebuggerAgentSdk)

            implementation(libs.bundles.ktorClient)
            implementation(libs.kotlinxCoroutinesCore)
            implementation(libs.kermit)
        }

        commonTest.dependencies {
            implementation(libs.kotlinTest)
            implementation(libs.ktorServerTestHost)
            implementation(libs.ktorServerWebSockets)
        }
    }
}
