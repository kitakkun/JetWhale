plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.metro)
    alias(libs.plugins.composeHotReload)
}

compose.desktop {
    application {
        mainClass = "com.kitakkun.jetwhale.host.MainKt"
        nativeDistributions {
            jvmArgs(
                "-Dapple.awt.application.appearance=system"
            )
        }
    }
}

compose.resources {
    packageOfResClass = "com.kitakkun.jetwhale.host"
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=soil.query.annotation.ExperimentalSoilQueryApi")
    }
}

dependencies {
    implementation(projects.jetwhaleDebuggerHostSdk)
    implementation(projects.jetwhaleProtocol)
    implementation(projects.jetwhaleDebuggerHost.feature.settings)
    implementation(projects.jetwhaleDebuggerHost.feature.plugin)
    implementation(projects.jetwhaleDebuggerHost.core.model)
    implementation(projects.jetwhaleDebuggerHost.core.data)
    implementation(projects.jetwhaleDebuggerHost.core.architecture)
    implementation(projects.jetwhaleDebuggerHost.core.ui)

    implementation(libs.bundles.navigation3)
    implementation(libs.kotlinxSerializationJson)
    implementation(libs.kotlinxCollectionsImmutable)
    implementation(libs.soilQueryCompose)
    implementation(libs.androidxDatastorePreferences)
    implementation(libs.rin)
    implementation(libs.material3)

    implementation(compose.materialIconsExtended)
    testImplementation(libs.kotlinTest)
}
