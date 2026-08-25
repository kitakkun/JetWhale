plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "com.kitakkun.jetwhale.demo"
    compileSdk = 37

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
    // Compose Semantics Inspector probe: reaching a live composition is Android-only for now, so it is
    // wired up here rather than in the shared module.
    implementation(projects.jetwhalePlugins.semantics.agent)
    // The activity lays the shared Compose app out above a strip of plain Android Views, which is
    // what gives the Compose Semantics Inspector's Android View support something to report.
    implementation(libs.jetbrainsComposeFoundation)
    implementation(libs.androidxActivityCompose)
    implementation(libs.androidxActivityKtx)
    implementation(libs.androidxCoreKtx)
}
