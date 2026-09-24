import com.vanniktech.maven.publish.MavenPublishBaseExtension
import util.JetWhalePublishExtension

plugins {
    id("com.vanniktech.maven.publish")
}

extensions.create("jetwhalePublish", JetWhalePublishExtension::class)

afterEvaluate {
    val jetwhalePublish = extensions.getByType(JetWhalePublishExtension::class)

    val artifactName = jetwhalePublish.name
    val artifactId = jetwhalePublish.artifactId
    val artifactDescription = jetwhalePublish.description

    if (artifactName.isBlank()) {
        logger.warn("jetwhalePublish.name is not set. Skipping publishing configuration.")
        return@afterEvaluate
    }

    if (artifactId.isBlank()) {
        logger.warn("jetwhalePublish.artifactId is not set. Skipping publishing configuration.")
        return@afterEvaluate
    }

    if (artifactDescription.isBlank()) {
        logger.warn("jetwhalePublish.description is not set. Skipping publishing configuration.")
        return@afterEvaluate
    }

    logger.info("Configuring publishing for $artifactName ($artifactId)")

    // -PjetwhalePublishOnly=<selectors> restricts a `publishToMavenCentral` run to some artifacts;
    // the others keep their publications configured but their publish tasks disabled, so one
    // invocation still covers the whole build and skips what was not asked for.
    val selectors = findProperty("jetwhalePublishOnly")?.toString()?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
    if (selectors != null && selectors.none { it.selects(artifactId) }) {
        logger.lifecycle("Not publishing $artifactId: not selected by jetwhalePublishOnly=${selectors.joinToString(",")}")
        tasks.matching { it.name.startsWith("publish") }.configureEach { enabled = false }
    }

    configure<MavenPublishBaseExtension> {
        publishToMavenCentral()
        signAllPublications()
        coordinates("com.kitakkun.jetwhale", artifactId, version.toString())

        pom {
            name = artifactName
            description = artifactDescription
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
}

/**
 * A selector is an artifactId, or one of the groups the snapshot workflow offers: `sdk` (the
 * runtime, the SDKs and everything an app or a host plugin links against), one official plugin by
 * name (`network`, `nav3`, `semantics`, `storage`), `gradle-plugin` and `agent-plugin`.
 */
private fun String.selects(artifactId: String): Boolean = when (this) {
    artifactId -> true
    "network" -> artifactId.startsWith("jetwhale-network-inspector")
    "nav3" -> artifactId.startsWith("jetwhale-nav3-")
    "semantics" -> artifactId.startsWith("jetwhale-compose-semantics-inspector")
    "storage" -> artifactId.startsWith("jetwhale-storage-inspector")
    "gradle-plugin" -> artifactId == "jetwhale-host-gradle-plugin"
    "agent-plugin" -> artifactId == "jetwhale-agent-compiler-plugin" || artifactId == "jetwhale-agent-gradle-plugin"
    "sdk" -> listOf("network", "nav3", "semantics", "storage", "gradle-plugin", "agent-plugin").none { it.selects(artifactId) }
    else -> false
}
