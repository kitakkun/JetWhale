package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifestFile
import kotlinx.serialization.json.Json
import java.io.File
import java.util.jar.JarFile

internal const val PLUGIN_MANIFEST_PATH = "META-INF/jetwhale/plugin-manifest.json"

/** Parses a plugin manifest; keys this host version does not know are ignored. */
internal fun decodeJetWhaleHostPluginManifestFile(manifestJson: String): JetWhaleHostPluginManifestFile = pluginManifestJson.decodeFromString(manifestJson)

/** Reads [jar]'s plugin manifest straight from the archive, without loading any of its classes. */
internal fun readJetWhaleHostPluginManifestFile(jar: File): JetWhaleHostPluginManifestFile = JarFile(jar).use { archive ->
    val entry = archive.getJarEntry(PLUGIN_MANIFEST_PATH) ?: error("$PLUGIN_MANIFEST_PATH not found")
    decodeJetWhaleHostPluginManifestFile(archive.getInputStream(entry).bufferedReader().use { it.readText() })
}

private val pluginManifestJson = Json { ignoreUnknownKeys = true }
