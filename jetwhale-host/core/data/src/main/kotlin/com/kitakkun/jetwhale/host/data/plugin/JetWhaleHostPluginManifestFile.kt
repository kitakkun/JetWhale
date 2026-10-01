package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifestFile
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.jar.JarFile

internal const val PLUGIN_MANIFEST_PATH = "META-INF/jetwhale/plugin-manifest.json"

/** Parses a plugin manifest; keys this host version does not know are ignored. */
internal fun decodeJetWhaleHostPluginManifestFile(manifestJson: String): JetWhaleHostPluginManifestFile = pluginManifestJson.decodeFromString(manifestJson)

/** Reads [jar]'s plugin manifest straight from the archive, without loading any of its classes. */
internal fun readJetWhaleHostPluginManifestFile(jar: File): JetWhaleHostPluginManifestFile = JarFile(jar).use { archive ->
    val entry = archive.getJarEntry(PLUGIN_MANIFEST_PATH) ?: error("$PLUGIN_MANIFEST_PATH not found")
    decodeJetWhaleHostPluginManifestFile(archive.getInputStream(entry).use(InputStream::readPluginManifestJson))
}

/**
 * The plugins [jar] declares, or none when its manifest cannot be read (the load then fails and
 * says why). Read before loading, to find the running plugins a jar would take over.
 */
internal fun declaredPlugins(jar: File): List<JetWhaleHostPluginManifest> = try {
    readJetWhaleHostPluginManifestFile(jar).plugins
} catch (_: IOException) {
    emptyList()
} catch (_: IllegalStateException) {
    emptyList()
} catch (_: IllegalArgumentException) {
    emptyList()
}

/**
 * This stream's plugin manifest text, read only up to [MAX_PLUGIN_MANIFEST_BYTES]: a small archive
 * can inflate to any size, and a jar's manifest is read before anyone has approved the jar.
 */
internal fun InputStream.readPluginManifestJson(): String {
    val bytes = readNBytes(MAX_PLUGIN_MANIFEST_BYTES + 1)
    check(bytes.size <= MAX_PLUGIN_MANIFEST_BYTES) { "$PLUGIN_MANIFEST_PATH is larger than $MAX_PLUGIN_MANIFEST_BYTES bytes" }
    return bytes.decodeToString()
}

/** Far above any real manifest, which lists a handful of plugins. */
internal const val MAX_PLUGIN_MANIFEST_BYTES = 1024 * 1024

private val pluginManifestJson = Json { ignoreUnknownKeys = true }
