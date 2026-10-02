plugins {
    alias(libs.plugins.kotlinDsl)
    // Apply vanniktech (with the catalog version) before the `publish` convention, which applies it
    // without a version — so it resolves when this build is configured standalone (e.g. on CI).
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.publish)
}

group = "com.kitakkun.jetwhale"
version = libs.versions.jetwhale.get() + if (hasProperty("jetwhaleSnapshot")) "-SNAPSHOT" else ""

jetwhalePublish {
    artifactId = "jetwhale-host-gradle-plugin"
    name = "JetWhale Host Gradle Plugin"
    description = "Gradle plugin for developing JetWhale host plugins (packagePlugin, runJetWhale, runJetWhaleHot)."
}
