plugins {
    alias(libs.plugins.debuggerComposeFeature)
}

dependencies {
    implementation(projects.jetwhaleHostUi)
    implementation(projects.jetwhaleHost.core.model)
    implementation(projects.jetwhaleHost.core.architecture)
    implementation(libs.kotlinxCollectionsImmutable)
    implementation(libs.kotlinxDatetime)
    implementation(libs.aboutLibrariesCore)
    compileOnly(libs.androidxAnnotation)
    testImplementation(libs.kotlinTest)
    testImplementation(compose.desktop.currentOs)
    testImplementation(libs.jetbrainsComposeUiTestJUnit4)
}

compose.resources {
    packageOfResClass = "com.kitakkun.jetwhale.host.settings"
}

// aboutlibraries ships Java 21 class files, which the module's Java 17 toolchain cannot load.
tasks.test {
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
