import util.PublishedVersions

plugins {
    `version-catalog`
    alias(libs.plugins.publish)
}

// The primary way to consume a release: one `from("com.kitakkun.jetwhale:jetwhale-catalog:<version>")`
// in settings gives versionless aliases for every artifact of that release, including the Gradle
// plugin, which a BOM cannot carry. Resolved at settings time, so it is unaffected by the variant
// matching that Kotlin Multiplatform targets rely on.
// Gradle resolves these by plugin id, so they belong in [plugins] rather than [libraries]. The agent
// *compiler* plugin is not here: consumers never name it, the Kotlin Gradle plugin fetches it.
val gradlePlugins = mapOf(
    "jetwhale-host-gradle-plugin" to ("host" to "com.kitakkun.jetwhale.host"),
    "jetwhale-agent-gradle-plugin" to ("agent" to "com.kitakkun.jetwhale.agent"),
)

catalog {
    versionCatalog {
        version("jetwhale", project.version.toString())
        PublishedVersions.publishVersions(project).forEach { (artifactId, artifactVersion) ->
            val gradlePlugin = gradlePlugins[artifactId]
            when {
                // The catalog and the BOM are the entry points; they do not list each other.
                artifactId == "jetwhale-bom" || artifactId == "jetwhale-catalog" -> Unit

                gradlePlugin != null -> plugin(gradlePlugin.first, gradlePlugin.second).version(artifactVersion)

                else -> library(artifactId.removePrefix("jetwhale-"), "com.kitakkun.jetwhale", artifactId)
                    .version(artifactVersion)
            }
        }
    }
}

jetwhalePublish {
    artifactId = "jetwhale-catalog"
    name = "JetWhale Version Catalog"
    description = "Gradle version catalog naming every JetWhale artifact and plugin of one release."
}
