import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import util.JetWhalePublishExtension

plugins {
    id("com.vanniktech.maven.publish")
}

extensions.create("jetwhalePublish", JetWhalePublishExtension::class)

val gitRepositoryRootDir: File = generateSequence(rootDir, File::getParentFile).first { File(it, "LICENSE").isFile }

// Apache-2.0 asks every redistribution to carry the license, and the notices credit the icon
// artwork the jars bundle.
val legalFiles = listOf(File(gitRepositoryRootDir, "LICENSE"), File(gitRepositoryRootDir, "THIRD_PARTY_NOTICES.md"))
    .filter(File::isFile)

tasks.withType<Jar>().configureEach {
    from(legalFiles) { into("META-INF") }
}

// Classes compiled here can carry the lint plugin's inferred-fact annotations, whose classes are
// never published; an app shrinking the Android artifact with R8 would otherwise stop on them.
pluginManager.withPlugin("com.android.kotlin.multiplatform.library") {
    extensions.getByType<KotlinMultiplatformExtension>().extensions.configure<KotlinMultiplatformAndroidLibraryExtension> {
        optimization {
            consumerKeepRules.publish = true
            consumerKeepRules.file(rootProject.file("gradle/consumer-rules/kotrail.pro"))
        }
    }
}

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

    // publishToMavenCentral uploads one Central Portal deployment per Gradle build, and each
    // deployment counts against the namespace's monthly release limit. A release stages the
    // artifacts of every build, the included Gradle plugin builds too, in this one directory, and
    // the Publish workflow uploads it as a single deployment.
    publishing {
        repositories {
            maven {
                name = "mavenCentralBundle"
                url = uri(gitRepositoryRootDir.resolve("build/maven-central-bundle"))
            }
        }
    }
}

/**
 * A selector is an artifactId, or one of the groups the snapshot workflow offers: `sdk` (the
 * runtime, the SDKs and everything an app or a host plugin links against), one official plugin by
 * name (`network`, `nav3`, `semantics`, `storage`, `actions`, `mirror`, `soil`), `gradle-plugin` and `agent-plugin`.
 */
private fun String.selects(artifactId: String): Boolean = when (this) {
    artifactId -> true
    "network" -> artifactId.startsWith("jetwhale-network-inspector")
    "nav3" -> artifactId.startsWith("jetwhale-nav3-")
    "semantics" -> artifactId.startsWith("jetwhale-compose-semantics-inspector")
    "storage" -> artifactId.startsWith("jetwhale-storage-inspector")
    "actions" -> artifactId.startsWith("jetwhale-debug-actions")
    "mirror" -> artifactId == "jetwhale-device-mirror"
    "soil" -> artifactId.startsWith("jetwhale-soil-inspector")
    "gradle-plugin" -> artifactId == "jetwhale-host-gradle-plugin"
    "agent-plugin" -> artifactId == "jetwhale-agent-compiler-plugin" || artifactId == "jetwhale-agent-gradle-plugin"
    "sdk" -> listOf("network", "nav3", "semantics", "storage", "actions", "mirror", "soil", "gradle-plugin", "agent-plugin").none { it.selects(artifactId) }
    else -> false
}
