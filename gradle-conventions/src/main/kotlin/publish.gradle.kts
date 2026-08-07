import com.vanniktech.maven.publish.MavenPublishBaseExtension
import util.JetWhalePublishExtension
import util.PublishedVersions
import util.registerAggregatePublishTask

plugins {
    id("com.vanniktech.maven.publish")
}

extensions.create("jetwhalePublish", JetWhalePublishExtension::class)

afterEvaluate {
    val jetwhalePublish = extensions.getByType(JetWhalePublishExtension::class)

    val artifactName = jetwhalePublish.name
    val artifactId = jetwhalePublish.artifactId
    val artifactDescription = jetwhalePublish.description

    // Hard failures rather than warnings: a module that silently skips publishing also breaks the
    // coordinates the BOM and the published version catalog promise for it.
    require(artifactName.isNotBlank()) { "jetwhalePublish.name is not set for $path." }
    require(artifactId.isNotBlank()) { "jetwhalePublish.artifactId is not set for $path." }
    require(artifactDescription.isNotBlank()) { "jetwhalePublish.description is not set for $path." }

    val trainVersion = version.toString()
    // Each artifact keeps the release version it was last published under, so an unchanged module is
    // not re-uploaded.
    val publishVersion = PublishedVersions.publishVersionFor(project, artifactId)

    logger.info("Configuring publishing for $artifactName ($artifactId:$publishVersion)")

    configure<MavenPublishBaseExtension> {
        publishToMavenCentral()
        signAllPublications()
        coordinates("com.kitakkun.jetwhale", artifactId, publishVersion)

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

    // Only the modules due for republishing join the aggregate task, so the Central Portal
    // deployment holds exactly them. Every build gets its own aggregate, including the included
    // builds: publishing one of those wholesale would re-upload an artifact that stays at an older
    // version, and Central rejects that because its releases are immutable.
    val aggregate = registerAggregatePublishTask(rootProject)
    if (publishVersion == trainVersion) {
        aggregate.configure { dependsOn(tasks.named("publishToMavenCentral")) }
    }
}
