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

    if (artifactName.isBlank()) {
        logger.warn("jetwhalePublish.name is not set. Skipping publishing configuration.")
        return@afterEvaluate
    }

    if (artifactId.isBlank()) {
        logger.warn("jetwhalePublish.artifactId is not set. Skipping publishing configuration.")
        return@afterEvaluate
    }

    logger.info("Configuring publishing for $artifactName ($artifactId)")

    configure<MavenPublishBaseExtension> {
        publishToMavenCentral()
        signAllPublications()
        coordinates("com.kitakkun.jetwhale", artifactId, version.toString())

        pom {
            name = artifactName
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
