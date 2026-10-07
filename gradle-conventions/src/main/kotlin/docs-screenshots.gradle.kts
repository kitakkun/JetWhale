import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    id("org.jetbrains.kotlin.jvm")
}

val kotlinJvmExtension = extensions.getByType<KotlinJvmProjectExtension>()

/**
 * The `docsScreenshots` compilation: tests that render this module's real UI with fixture state into
 * the user guides' screenshots, `docs/images/<page>/<shot>-{light,dark}.webp`.
 *
 * The compilation sees the module's `internal` declarations, and those of its `preview` compilation
 * when there is one, so a shot can render the same screens and fixtures the previews do. `check`
 * compiles it; only `recordDocsScreenshots` runs it, since the images it writes depend on the fonts of
 * the machine that renders them.
 */
val docsScreenshotsCompilation = kotlinJvmExtension.target.compilations.create("docsScreenshots") {
    associateWith(kotlinJvmExtension.target.compilations.getByName("main"))
}
kotlinJvmExtension.target.compilations.matching { it.name == "preview" }.all {
    docsScreenshotsCompilation.associateWith(this)
}

// What the host supplies to a plugin at runtime is compileOnly in the plugin; a shot runs without
// the host, so it needs those classes itself.
configurations.named(docsScreenshotsCompilation.runtimeOnlyConfigurationName) {
    extendsFrom(configurations.getByName("compileOnly"))
}

dependencies {
    add(docsScreenshotsCompilation.implementationConfigurationName, project(":tools:docs-screenshots"))
    add(docsScreenshotsCompilation.implementationConfigurationName, "org.jetbrains.kotlin:kotlin-test-junit")
}

val docsImagesDirectoryPath = rootProject.layout.projectDirectory.dir("docs/images").asFile.absolutePath

tasks.register<Test>("recordDocsScreenshots") {
    group = "documentation"
    description = "Renders this module's screenshots for the user guides into docs/images."
    testClassesDirs = docsScreenshotsCompilation.output.classesDirs
    classpath = docsScreenshotsCompilation.output.allOutputs + docsScreenshotsCompilation.runtimeDependencyFiles
    useJUnit()
    systemProperty("jetwhale.docs.imagesDir", docsImagesDirectoryPath)
    // The UI's text and times follow the JVM's locale and time zone, which differ from machine to
    // machine.
    jvmArgs("-Duser.language=en", "-Duser.country=US", "-Duser.timezone=UTC")
    doNotTrackState("Writes into docs/images, which only it decides whether to change")
}

tasks.named("check") {
    dependsOn(docsScreenshotsCompilation.compileTaskProvider)
}
