package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.MavenCoordinates
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginDependencyManifestTest {
    @Test
    fun `reads coordinates one per line skipping blanks and comments`() {
        val jar = jarWith(
            """
            # external runtime dependencies
            org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.11.0

            org.jetbrains.kotlin:kotlin-stdlib:2.4.10
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                MavenCoordinates(groupId = "org.jetbrains.kotlinx", artifactId = "kotlinx-serialization-json-jvm", version = "1.11.0"),
                MavenCoordinates(groupId = "org.jetbrains.kotlin", artifactId = "kotlin-stdlib", version = "2.4.10"),
            ),
            PluginDependencyManifest.readFrom(jar),
        )
    }

    private fun jarWith(manifestContent: String?): File {
        val jar = File.createTempFile("plugin", ".jar").apply { deleteOnExit() }
        JarOutputStream(jar.outputStream()).use { out ->
            out.putNextEntry(JarEntry("META-INF/jetwhale/plugin-manifest.json"))
            out.write("{}".toByteArray())
            out.closeEntry()
            if (manifestContent != null) {
                out.putNextEntry(JarEntry(PluginDependencyManifest.JAR_ENTRY))
                out.write(manifestContent.toByteArray())
                out.closeEntry()
            }
        }
        return jar
    }

    @Test
    fun `jar without manifest resolves to no dependencies`() {
        assertEquals(emptyList(), PluginDependencyManifest.readFrom(jarWith(null)))
    }

    @Test
    fun `empty manifest resolves to no dependencies`() {
        assertEquals(emptyList(), PluginDependencyManifest.readFrom(jarWith("\n")))
    }
}
