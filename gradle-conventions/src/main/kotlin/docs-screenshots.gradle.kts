import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    id("org.jetbrains.kotlin.jvm")
}

val kotlinJvm = extensions.getByType<KotlinJvmProjectExtension>()

/**
 * The `docsScreenshots` compilation: tests that render this module's real UI with fixture state into
 * the user guides' screenshots, `docs/images/<page>/<shot>-{light,dark}.webp`.
 *
 * The compilation sees the module's `internal` declarations, and those of its `preview` compilation
 * when there is one, so a shot can render the same screens and fixtures the previews do. `check`
 * compiles it; only `recordDocsScreenshots` runs it, since the images it writes depend on the fonts of
 * the machine that renders them.
 */
val docsScreenshots = kotlinJvm.target.compilations.create("docsScreenshots") {
    associateWith(kotlinJvm.target.compilations.getByName("main"))
}
kotlinJvm.target.compilations.matching { it.name == "preview" }.all {
    docsScreenshots.associateWith(this)
}

// What the host supplies to a plugin at runtime is compileOnly in the plugin; a shot runs without the
// host, so it needs those classes itself.
configurations.named(docsScreenshots.runtimeOnlyConfigurationName) {
    extendsFrom(configurations.getByName("compileOnly"))
}

dependencies {
    add(docsScreenshots.implementationConfigurationName, project(":tools:docs-screenshots"))
    add(docsScreenshots.implementationConfigurationName, "org.jetbrains.kotlin:kotlin-test-junit")
}

val docsImagesDirectory = rootProject.layout.projectDirectory.dir("docs/images").asFile.absolutePath

tasks.register<Test>("recordDocsScreenshots") {
    group = "documentation"
    description = "Renders this module's screenshots for the user guides into docs/images."
    testClassesDirs = docsScreenshots.output.classesDirs
    classpath = docsScreenshots.output.allOutputs + docsScreenshots.runtimeDependencyFiles
    useJUnit()
    systemProperty("jetwhale.docs.imagesDir", docsImagesDirectory)
    // Text and times follow the JVM's locale and time zone; pinning them keeps the images the same
    // whatever machine renders them, short of its fonts.
    jvmArgs("-Duser.language=en", "-Duser.country=US", "-Duser.timezone=UTC")
    doNotTrackState("Writes into docs/images, which only it decides whether to change")
}

tasks.named("check") {
    dependsOn(docsScreenshots.compileTaskProvider)
}
