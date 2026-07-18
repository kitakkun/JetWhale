package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.MavenCoordinates
import java.io.File
import java.util.jar.JarFile

/**
 * Reads the external-dependency manifest that Maven-published plugin jars carry at
 * [JAR_ENTRY]: one `group:artifact:version` line per external runtime dependency, written by the
 * JetWhale Gradle plugin from the dependency set Gradle resolved at build time.
 *
 * The host downloads each listed jar next to the plugin (see `AppDataDirectoryProvider`'s plugin
 * libs directory) and puts them on the plugin's classpath. Locally packaged fat-jars carry no
 * manifest and resolve to an empty list, keeping the pre-existing flow unchanged.
 */
object PluginDependencyManifest {
    const val JAR_ENTRY = "META-INF/jetwhale/dependencies.txt"

    fun readFrom(pluginJar: File): List<MavenCoordinates> = JarFile(pluginJar).use { jar ->
        val entry = jar.getJarEntry(JAR_ENTRY) ?: return emptyList()
        jar.getInputStream(entry).bufferedReader().readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { MavenCoordinates.parse(it) }
    }
}
