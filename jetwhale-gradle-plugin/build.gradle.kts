import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    // kotlin-dsl applies java-gradle-plugin and registers the `jetwhale-plugin` precompiled script
    // plugin in src/main/kotlin, so it can be published as a consumable Gradle plugin.
    alias(libs.plugins.kotlinDsl)
    alias(libs.plugins.mavenPublish)
}

group = "com.kitakkun.jetwhale"
version = libs.versions.jetwhale.get()

configure<MavenPublishBaseExtension> {
    publishToMavenCentral()
    signAllPublications()
    coordinates("com.kitakkun.jetwhale", "jetwhale-gradle-plugin", version.toString())

    pom {
        name = "JetWhale Gradle Plugin"
        description = "Gradle plugin for developing JetWhale host plugins (packagePlugin, runJetWhale, runJetWhaleFromRelease)."
        inceptionYear = "2026"
        url = "https://github.com/kitakkun/jetwhale"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "kitakkun"
                name = "kitakkun"
                url = "https://github.com/kitakkun"
            }
        }
        scm {
            url = "https://github.com/kitakkun/jetwhale"
            connection = "scm:git:git://github.com/kitakkun/jetwhale.git"
            developerConnection = "scm:git:ssh://git@github.com/kitakkun/jetwhale.git"
        }
    }
}
