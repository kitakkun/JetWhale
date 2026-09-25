package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifestFile
import kotlinx.serialization.json.Json
import java.io.File
import java.util.jar.JarFile

internal const val PLUGIN_MANIFEST_PATH = "META-INF/jetwhale/plugin-manifest.json"

/** Parses a plugin manifest; keys this host version does not know are ignored. */
internal fun decodeJetWhaleHostPluginManifestFile(manifestJson: String): JetWhaleHostPluginManifestFile = pluginManifestJson.decodeFromString(manifestJson)

/**
 * Reads [jar]'s plugin manifest straight from the archive, without loading any of its classes. Jars
 * that merely appear in the plugins directory are read before anyone approves them, so the manifest
 * is read only up to [MAX_PLUGIN_MANIFEST_BYTES]: a small archive can inflate to any size.
 */
internal fun readJetWhaleHostPluginManifestFile(jar: File): JetWhaleHostPluginManifestFile = JarFile(jar).use { archive ->
    val entry = archive.getJarEntry(PLUGIN_MANIFEST_PATH) ?: error("$PLUGIN_MANIFEST_PATH not found")
    val bytes = archive.getInputStream(entry).use { it.readNBytes(MAX_PLUGIN_MANIFEST_BYTES + 1) }
    check(bytes.size <= MAX_PLUGIN_MANIFEST_BYTES) { "$PLUGIN_MANIFEST_PATH is larger than $MAX_PLUGIN_MANIFEST_BYTES bytes" }
    decodeJetWhaleHostPluginManifestFile(bytes.decodeToString())
}

/** Far above any real manifest, which lists a handful of plugins. */
internal const val MAX_PLUGIN_MANIFEST_BYTES = 1024 * 1024

private val pluginManifestJson = Json { ignoreUnknownKeys = true }
