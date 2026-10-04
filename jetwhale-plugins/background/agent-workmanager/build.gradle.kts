plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.publish)
}

group = "com.kitakkun.jetwhale.plugins.background"

// Kotlin's ABI validation skips Android targets, so this Android-only module has no ABI dump.
kotlin {
    jvmToolchain(17)

    android {
        namespace = "com.kitakkun.jetwhale.plugins.background.agent.workmanager"
        compileSdk = 37
        minSdk = 23

        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            api(projects.jetwhalePlugins.background.agent)
            api(libs.androidxWorkRuntime)
            implementation(libs.kotlinxCoroutinesCore)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.kotlinTest)
            implementation(libs.kotlinxCoroutinesTest)
            implementation(libs.androidxWorkTesting)
            implementation(libs.robolectric)
            implementation(libs.androidxTestCore)
            implementation(libs.junit)
        }
    }
}

tasks.withType<Test>().configureEach {
    // Robolectric loads a whole Android framework into the test JVM.
    maxHeapSize = "2g"
}

jetwhalePublish {
    artifactId = "jetwhale-background-work-agent-workmanager"
    name = "JetWhale Background Work Agent (WorkManager)"
    description = "WorkManager adapter that shows an app's WorkManager work in the JetWhale Background Work plugin."
}
