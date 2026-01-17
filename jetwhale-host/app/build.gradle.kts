import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.metro)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.aboutLibraries)
}

compose.desktop {
    application {
        mainClass = "com.kitakkun.jetwhale.host.MainKt"
        nativeDistributions {
            packageName = "JetWhale Debugger"
            // Remove pre-release suffix for package version
            packageVersion = libs.versions.jetwhale.get().substringBefore("-")
            licenseFile = rootProject.rootDir.resolve("LICENSE")

            // Fix runtime NoClassDefFoundError which occurs only on packaged application
            modules("jdk.unsupported")
            modules("java.naming")

            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Msi,
                TargetFormat.Deb,
            )
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
    implementation(projects.jetwhaleHostSdk)
    implementation(projects.jetwhaleProtocol)
    implementation(projects.jetwhaleHost.feature.settings)
    implementation(projects.jetwhaleHost.feature.plugin)
    implementation(projects.jetwhaleHost.core.model)
    implementation(projects.jetwhaleHost.core.data)
    implementation(projects.jetwhaleHost.core.architecture)
    implementation(projects.jetwhaleHost.core.ui)

    implementation(libs.bundles.navigation3)
    implementation(libs.kotlinxSerializationJson)
    implementation(libs.kotlinxCollectionsImmutable)
    implementation(libs.soilQueryCompose)
    implementation(libs.androidxDatastorePreferences)
    implementation(libs.rin)
    implementation(libs.material3)
    implementation(libs.aboutLibrariesCore)

    implementation(compose.materialIconsExtended)
    testImplementation(libs.kotlinTest)
}

aboutLibraries {
    export {
        outputFile = file("src/main/composeResources/files/aboutlibraries.json")
    }
    collect {
        this.configPath = file("aboutlibraries")
    }
}
