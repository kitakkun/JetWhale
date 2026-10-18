package com.kitakkun.jetwhale.protocol.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

/**
 * The `com.kitakkun.jetwhale.protocol` plugin, for a JetWhale plugin's protocol module.
 *
 * It generates the module's `JetWhalePluginContract` into the common source set and its JSON form
 * into the JVM jar, and adds `dumpWireSchema`, which writes the wire schema of the compiled classes to
 * `wire/<project>.wire`, and `checkWireSchema`, which runs in `check` and fails when that dump is out
 * of date. The module applies the Kotlin multiplatform plugin with one JVM target, or the Kotlin JVM
 * plugin: the schema is read from the JVM classes.
 *
 * Passing `-PjetwhaleWireSchemaBaselineDir=<dir>`, a directory laid out like the root project that
 * holds an earlier revision of each `wire/<project>.wire`, makes `checkWireSchema` also refuse a
 * committed dump that could not have followed the earlier one, and, for a module the directory has
 * no dump of, a first dump at another revision than 1. A relative `<dir>` is resolved
 * against the root project.
 */
class JetWhaleProtocolPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("jetwhaleProtocol", JetWhaleProtocolExtension::class.java)
        val committedWireSchemaLocation = project.layout.projectDirectory.file("wire/${project.name}.wire")
        val rootDirectory = project.rootDir
        val wireSchemaPathFromRoot = project.projectDir.relativeTo(rootDirectory).resolve("wire/${project.name}.wire").path
        val projectVersion = project.provider { project.version.toString() }

        val generateContract = project.tasks.register("generateJetWhalePluginContract", GenerateJetWhalePluginContractTask::class.java) {
            description = "Generates the JetWhalePluginContract of this protocol module."
            pluginId.set(extension.pluginId)
            releaseVersion.set(projectVersion)
            protocolRevision.set(extension.protocolRevision)
            contractQualifiedName.set(extension.contractQualifiedName)
            committedWireSchemaFile.from(committedWireSchemaLocation)
            kotlinSourceDirectory.set(project.layout.buildDirectory.dir("generated/jetwhale/contract/kotlin"))
            resourceDirectory.set(project.layout.buildDirectory.dir("generated/jetwhale/contract/resources"))
        }

        val extractWireSchema = project.tasks.register("extractWireSchema", ExtractWireSchemaTask::class.java) {
            description = "Reads the wire schema of the compiled JVM classes into the build directory."
            protocolRevision.set(extension.protocolRevision)
            extractedWireSchemaFile.set(project.layout.buildDirectory.file("jetwhale/wire/${project.name}.wire"))
        }

        val dumpWireSchema = project.tasks.register("dumpWireSchema", DumpWireSchemaTask::class.java) {
            group = "jetwhale"
            description = "Writes the wire schema of the compiled classes to wire/${project.name}.wire, refusing a breaking change without a new protocolRevision."
            extractedWireSchemaFile.set(extractWireSchema.flatMap { it.extractedWireSchemaFile })
            committedWireSchemaFile.set(committedWireSchemaLocation)
        }

        val checkWireSchema = project.tasks.register("checkWireSchema", CheckWireSchemaTask::class.java) {
            group = "verification"
            description = "Fails when wire/${project.name}.wire is not the wire schema of the compiled classes at the declared protocolRevision."
            extractedWireSchemaFile.set(extractWireSchema.flatMap { it.extractedWireSchemaFile })
            committedWireSchemaFile.from(committedWireSchemaLocation)
            baselineWireSchemaFile.from(
                project.providers.gradleProperty("jetwhaleWireSchemaBaselineDir")
                    .map { listOf(rootDirectory.resolve(it).resolve(wireSchemaPathFromRoot)) }
                    .orElse(emptyList()),
            )
            mustRunAfter(dumpWireSchema)
        }

        project.pluginManager.withPlugin("base") {
            project.tasks.named("check") { dependsOn(checkWireSchema) }
        }

        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            kotlin.sourceSets.getByName("commonMain").kotlin.srcDir(generateContract.flatMap { it.kotlinSourceDirectory })
            kotlin.targets.matching { it.platformType == KotlinPlatformType.jvm }.all {
                connectJvmTargetToContractAndWireSchemaTasks(this, generateContract, extractWireSchema)
            }
        }
        project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            val kotlin = project.extensions.getByType(KotlinJvmProjectExtension::class.java)
            kotlin.sourceSets.getByName("main").kotlin.srcDir(generateContract.flatMap { it.kotlinSourceDirectory })
            connectJvmTargetToContractAndWireSchemaTasks(kotlin.target, generateContract, extractWireSchema)
        }

        project.afterEvaluate {
            val jvmTargetNames = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
                ?.targets?.filter { it.platformType == KotlinPlatformType.jvm }?.map { it.name }
                ?: project.extensions.findByType(KotlinJvmProjectExtension::class.java)?.let { listOf(it.target.name) }
                ?: throw GradleException(
                    "com.kitakkun.jetwhale.protocol reads the wire schema from JVM classes: apply the Kotlin multiplatform plugin " +
                        "with a JVM target, or the Kotlin JVM plugin, to ${project.path}.",
                )
            if (jvmTargetNames.size != 1) {
                throw GradleException(
                    "com.kitakkun.jetwhale.protocol reads the wire schema from the classes of one JVM target, but ${project.path} " +
                        "has ${jvmTargetNames.size}: $jvmTargetNames.",
                )
            }
        }
    }

    private fun connectJvmTargetToContractAndWireSchemaTasks(
        target: KotlinTarget,
        generateContract: TaskProvider<GenerateJetWhalePluginContractTask>,
        extractWireSchema: TaskProvider<ExtractWireSchemaTask>,
    ) {
        val mainCompilation = target.compilations.getByName(KotlinCompilation.MAIN_COMPILATION_NAME)
        mainCompilation.defaultSourceSet.resources.srcDir(generateContract.flatMap { it.resourceDirectory })
        extractWireSchema.configure {
            moduleClassDirectories.from(mainCompilation.output.classesDirs)
            mainCompilation.runtimeDependencyFiles?.let { runtimeClasspath.from(it) }
        }
    }
}
