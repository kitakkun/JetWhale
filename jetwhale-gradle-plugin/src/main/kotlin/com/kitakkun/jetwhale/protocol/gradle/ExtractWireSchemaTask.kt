package com.kitakkun.jetwhale.protocol.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import javax.inject.Inject

/**
 * Reads the wire schema of the module's compiled JVM classes and writes it as a dump into the build
 * directory, where `dumpWireSchema` and `checkWireSchema` compare it with the committed one.
 *
 * Reading the serializers initializes the module's classes, so it runs in a separate worker process.
 */
@CacheableTask
internal abstract class ExtractWireSchemaTask : DefaultTask() {
    @get:Classpath
    abstract val moduleClassDirectories: ConfigurableFileCollection

    @get:Classpath
    abstract val runtimeClasspath: ConfigurableFileCollection

    @get:Input
    abstract val protocolRevision: Property<Int>

    @get:OutputFile
    abstract val extractedWireSchemaFile: RegularFileProperty

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @TaskAction
    fun extractWireSchema() {
        val task = this
        workerExecutor.processIsolation().submit(ExtractWireSchemaWorkAction::class.java) {
            moduleClassDirectories.from(task.moduleClassDirectories)
            runtimeClasspath.from(task.runtimeClasspath)
            protocolRevision.set(task.protocolRevision)
            extractedWireSchemaFile.set(task.extractedWireSchemaFile)
        }
    }
}

internal interface ExtractWireSchemaParameters : WorkParameters {
    val moduleClassDirectories: ConfigurableFileCollection
    val runtimeClasspath: ConfigurableFileCollection
    val protocolRevision: Property<Int>
    val extractedWireSchemaFile: RegularFileProperty
}

/**
 * Runs [WireSchemaReader] in a class loader of its own: the module's classes, the module's runtime
 * classpath and the reader, under the platform class loader. The worker's class loader holds
 * Gradle's Kotlin and the build script's classpath, whose kotlinx.serialization need not be the
 * module's.
 */
internal abstract class ExtractWireSchemaWorkAction : WorkAction<ExtractWireSchemaParameters> {
    override fun execute() {
        val moduleClassDirectories = parameters.moduleClassDirectories.files.toList()
        val readerLocation = File(ExtractWireSchemaWorkAction::class.java.protectionDomain.codeSource.location.toURI())
        val classLoaderUrls = (listOf(readerLocation) + moduleClassDirectories + parameters.runtimeClasspath.files)
            .map { it.toURI().toURL() }
            .toTypedArray()
        val renderedWireSchema = URLClassLoader(classLoaderUrls, ClassLoader.getPlatformClassLoader()).use { classLoader ->
            try {
                val reader = classLoader.loadClass(WireSchemaReader::class.java.name).getConstructor().newInstance()
                reader.javaClass
                    .getMethod("renderWireSchema", List::class.java, List::class.java, Int::class.javaPrimitiveType)
                    .invoke(reader, moduleClassDirectories, parameters.runtimeClasspath.files.toList(), parameters.protocolRevision.get()) as String
            } catch (e: InvocationTargetException) {
                val failure = e.targetException
                // The failure is the reader class loader's own WireSchemaRefusal, not this one, so
                // `is WireSchemaRefusal` would never match.
                if (failure.javaClass.name == WireSchemaRefusal::class.java.name) throw GradleException(failure.message.orEmpty())
                throw GradleException("Reading the wire schema failed:\n${failure.stackTraceToString()}")
            }
        }
        parameters.extractedWireSchemaFile.get().asFile.writeText(renderedWireSchema)
    }
}
