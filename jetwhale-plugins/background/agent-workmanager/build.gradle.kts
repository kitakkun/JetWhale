plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.publish)
}

group = "com.kitakkun.jetwhale.plugins.background"

// WorkManager is Android-only, and a separate artifact so that an app without it is not made to
// depend on it through the agent. Kotlin's ABI validation does not cover an Android-only target, so
// this module has no ABI dump.
kotlin {
    jvmToolchain(17)

    android {
        namespace = "com.kitakkun.jetwhale.plugins.background.agent.workmanager"
        compileSdk = 37
        minSdk = 23

        // Robolectric supplies the Context WorkManager's test driver needs.
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

// Robolectric loads a whole Android framework into the test JVM; a fixed ceiling keeps it from
// growing without bound on a busy machine.
tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
}

jetwhalePublish {
    artifactId = "jetwhale-background-work-agent-workmanager"
    name = "JetWhale Background Work Agent (WorkManager)"
    description = "WorkManager adapter that shows an app's WorkManager work in the JetWhale Background Work plugin."
}
