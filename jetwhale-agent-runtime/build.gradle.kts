plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.serialization)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.publish)
}

kotlin {
    androidLibrary {
        namespace = "com.kitakkun.jetwhale.agent.runtime"
        compileSdk = 36
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.jetwhaleProtocol.core)
            api(projects.jetwhaleProtocol.agent)
            api(projects.jetwhaleAgentSdk)

            implementation(libs.bundles.ktorClient)
            implementation(libs.kotlinxCoroutinesCore)
            implementation(libs.kermit)
        }

        commonTest.dependencies {
            implementation(projects.testAnnotations)
            implementation(libs.kotlinTest)
            implementation(libs.ktorServerTestHost)
            implementation(libs.ktorServerWebSockets)
        }
    }
}

jetwhalePublish {
    artifactId = "jetwhale-agent-runtime"
    name = "JetWhale Agent Runtime"
    description = "Runtime library for JetWhale Agent"
}
