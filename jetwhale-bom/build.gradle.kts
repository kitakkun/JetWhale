import util.PublishedVersions

plugins {
    `java-platform`
    alias(libs.plugins.publish)
}

// The Gradle plugins are left out because `plugins { }` needs a literal version, which dependency
// management cannot supply — the published version catalog carries them instead. The BOM and the
// catalog themselves are the entry points, so they do not constrain each other.
val artifactsWithoutConstraint = setOf(
    "jetwhale-bom",
    "jetwhale-catalog",
    "jetwhale-host-gradle-plugin",
    "jetwhale-agent-gradle-plugin",
)

// A release republishes only the artifacts that changed, so their versions drift apart. This BOM
// pins the combination that belongs to one release, letting consumers name a single version.
dependencies {
    constraints {
        PublishedVersions.publishVersions(project)
            .filterKeys { it !in artifactsWithoutConstraint }
            .forEach { (artifactId, version) -> api("com.kitakkun.jetwhale:$artifactId:$version") }
    }
}

jetwhalePublish {
    artifactId = "jetwhale-bom"
    name = "JetWhale BOM"
    description = "Bill of materials pinning every JetWhale artifact to the versions of one release."
}
