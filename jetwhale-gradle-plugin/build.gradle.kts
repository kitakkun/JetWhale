plugins {
    // kotlin-dsl applies java-gradle-plugin and registers the `jetwhale-plugin` precompiled script
    // plugin in src/main/kotlin, so it can be published as a consumable Gradle plugin.
    alias(libs.plugins.kotlinDsl)
    // Apply vanniktech (with the catalog version) before the `publish` convention, which applies it
    // without a version — so it resolves when this build is configured standalone (e.g. on CI).
    alias(libs.plugins.mavenPublish)
    // Shared convention: configures vanniktech maven-publish (Maven Central + signing + POM) from the
    // jetwhalePublish { } values below.
    alias(libs.plugins.publish)
}

group = "com.kitakkun.jetwhale"
version = libs.versions.jetwhale.get()

jetwhalePublish {
    artifactId = "jetwhale-gradle-plugin"
    name = "JetWhale Gradle Plugin"
    description = "Gradle plugin for developing JetWhale host plugins (packagePlugin, runJetWhale, runJetWhaleFromRelease)."
}
