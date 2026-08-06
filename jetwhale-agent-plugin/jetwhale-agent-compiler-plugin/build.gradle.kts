plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.publish)
}

group = "com.kitakkun.jetwhale"
version = libs.versions.jetwhale.get() + if (hasProperty("jetwhaleSnapshot")) "-SNAPSHOT" else ""

/**
 * Which Kotlin compiler API this plugin is compiled against — that is, which version's JAR ships.
 *
 * Deliberately separate from `kotlin.compiler`, which is the *consumer's* Kotlin and drives the
 * Kotlin Gradle plugin for the whole build. Keeping them apart is what lets CI compile `sample` with
 * Kotlin 2.3 while the plugin acting on it was built against 2.4 — the arrangement a consumer on 2.3
 * actually gets from a single published artifact, and therefore the one worth testing.
 *
 * Set both to the same value to sweep source compatibility instead: `-Pkotlin.compiler=2.3.0
 * -Pkotlin.plugin.api=2.3.0` proves the source only touches API that 2.3 already had.
 */
val kotlinPluginApi: String = providers.gradleProperty("kotlin.plugin.api")
    .orElse(libs.versions.kotlin)
    .get()

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // The plugin API is @ExperimentalCompilerApi by design; opting in per-file would be noise.
        freeCompilerArgs.add("-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
    }
}

dependencies {
    // compileOnly, always: kotlinc already has these on its classloader, and bundling them again
    // produces duplicate-class conflicts that stop the plugin loading at all.
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinPluginApi")
    compileOnly(kotlin("stdlib"))

    // Runners live in `test` rather than test-fixtures so they can see the plugin's `internal`
    // declarations — Kotlin associates `test` with `main`, test-fixtures is its own compilation.
    // The test framework links against the *un-shaded* kotlin-compiler, so the tests get that while
    // the published JAR keeps compiling against the embeddable one. Safe here only because this
    // plugin references no IntelliJ classes at all — verified by grepping the compiled bytecode for
    // `com/intellij` — so the same .class files load under either compiler.
    testImplementation("org.jetbrains.kotlin:kotlin-compiler:$kotlinPluginApi")
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-internal-test-framework:$kotlinPluginApi")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:$kotlinPluginApi")
    // The framework still reaches for JUnit 4 at runtime despite running on the JUnit 5 platform.
    testRuntimeOnly("junit:junit:4.13.2")
}

/** Jars the test framework locates by absolute path, handed over as system properties below. */
val testArtifacts: Configuration by configurations.creating

dependencies {
    testArtifacts("org.jetbrains.kotlin:kotlin-stdlib:$kotlinPluginApi")
    testArtifacts("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinPluginApi")
    testArtifacts("org.jetbrains.kotlin:kotlin-reflect:$kotlinPluginApi")
    testArtifacts("org.jetbrains.kotlin:kotlin-test:$kotlinPluginApi")
    testArtifacts("org.jetbrains.kotlin:kotlin-script-runtime:$kotlinPluginApi")
    testArtifacts("org.jetbrains.kotlin:kotlin-annotations-jvm:$kotlinPluginApi")
}

sourceSets {
    test {
        resources.setSrcDirs(listOf("testData"))
    }
}

tasks.test {
    dependsOn(testArtifacts)
    useJUnitPlatform()
    // testData paths in the runners are resolved against this.
    workingDir = layout.projectDirectory.asFile

    systemProperty("idea.home.path", layout.projectDirectory.asFile.path)
    systemProperty("idea.ignore.disabled.plugins", "true")

    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-stdlib", "kotlin-stdlib")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-stdlib-jdk8", "kotlin-stdlib-jdk8")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-reflect", "kotlin-reflect")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-test", "kotlin-test")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-script-runtime", "kotlin-script-runtime")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-annotations-jvm", "kotlin-annotations-jvm")
}

fun Test.setLibraryProperty(propName: String, jarName: String) {
    val path = testArtifacts.files
        .find { """$jarName-\d.*""".toRegex().matches(it.name) }
        ?.absolutePath
        ?: error("testArtifacts is missing $jarName")
    systemProperty(propName, path)
}

jetwhalePublish {
    artifactId = "jetwhale-agent-compiler-plugin"
    name = "JetWhale Agent Compiler Plugin"
    description = "Kotlin compiler plugin that bakes the build machine's address into buildMachineWss(port)."
}
