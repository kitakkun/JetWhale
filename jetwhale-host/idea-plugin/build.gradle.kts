import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.intellijPlatform)
}

// Declared here, not through the IntelliJ Platform settings plugin: that plugin switches off the
// Kotlin Gradle plugin's implicit stdlib dependency for every project in the build, which leaves
// the multiplatform modules compiling against whatever stdlib their libraries pull in.
repositories {
    mavenCentral()
    google()
    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(21)
}

// The host and its whole runtime classpath (Kotlin stdlib, coroutines, Compose, Ktor, ...) ship
// under `host/`, not `lib/`: the IDE puts only `lib/` on the plugin's classpath, so these jars stay
// invisible to it and are loaded by HostClassLoader instead, with the versions the host was built
// against rather than the ones the IDE bundles.
val hostRuntime: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.STANDARD_JVM))
        attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
    }
}

dependencies {
    intellijPlatform {
        intellijIdea(libs.versions.intellijIdea.get())
    }
    hostRuntime(projects.jetwhaleHost.ideaHost)
}

intellijPlatform {
    pluginConfiguration {
        id = "com.kitakkun.jetwhale"
        name = "JetWhale"
        version = libs.versions.jetwhale.get()
        ideaVersion {
            sinceBuild = "262"
        }
    }
}

// Jar file names alone collide (androidx.lifecycle and org.jetbrains.androidx.lifecycle both ship a
// lifecycle-common-jvm-2.11.0.jar), so each is prefixed with its group on the way in.
val hostJarNames: Provider<Map<File, String>> = hostRuntime.incoming.artifacts.resolvedArtifacts.map { artifacts ->
    artifacts.associate { artifact ->
        val group = (artifact.id.componentIdentifier as? ModuleComponentIdentifier)?.group ?: "jetwhale"
        artifact.file to "$group-${artifact.file.name}"
    }
}

// Every sandbox variant (build, runIde, tests) needs the host jars, not only the one buildPlugin zips.
tasks.withType<PrepareSandboxTask>().configureEach {
    // A local copy: a lambda that reads the script-level property would capture the script object,
    // which the configuration cache cannot serialize.
    val jarNames = hostJarNames
    from(hostRuntime) {
        into(intellijPlatform.projectName.map { "$it/host" })
        eachFile { name = jarNames.get().getValue(file) }
    }
}
