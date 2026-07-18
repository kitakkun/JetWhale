package com.kitakkun.jetwhale.host.data

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import okio.Path
import okio.Path.Companion.toPath
import java.io.File

@SingleIn(AppScope::class)
@Inject
class AppDataDirectoryProvider {
    private val homeDir = System.getProperty("user.home")
    private val appDataDir = "$homeDir/.jetwhale"
    private val pluginDir = "$appDataDir/plugins"
    private val pluginLibsDir = "$pluginDir/libs"
    private val dataStoreFilesDir = "$appDataDir/dataStorePreferences"
    private val pluginDataDir = "$appDataDir/plugin-data"
    private val sslDir = "$appDataDir/ssl"

    fun resolveDataStoreFilePath(fileName: String): Path = "$dataStoreFilesDir/$fileName".toPath()

    /**
     * Resolves the persistent store file for a single plugin. Each plugin gets its own directory so
     * plugins cannot reach each other's data. [pluginId] is sanitized first so a crafted id (e.g.
     * one containing path separators or `..`) cannot escape [pluginDataDir].
     */
    fun resolvePluginDataFilePath(pluginId: String): Path = "$pluginDataDir/${sanitizePluginId(pluginId)}/store.json".toPath()

    private fun sanitizePluginId(pluginId: String): String {
        val sanitized = buildString {
            for (c in pluginId) {
                append(if (c.isLetterOrDigit() || c == '.' || c == '-' || c == '_') c else '_')
            }
        }
        // Sanitization is lossy: distinct ids like "a/b" and "a_b" would otherwise collapse into the
        // same directory (breaking isolation and DataStore's single-instance-per-file rule). When any
        // character was replaced, a hash of the original id is appended to keep the name unique.
        val hashSuffix = "_" + pluginId.hashCode().toUInt().toString(16)
        return when {
            sanitized.isEmpty() || sanitized == "." || sanitized == ".." -> "plugin$hashSuffix"
            sanitized != pluginId -> sanitized + hashSuffix
            else -> sanitized
        }
    }

    fun getAppDataPath(): String = "~/.jetwhale"

    /**
     * The file backing the plugin trust registry (the list of jars the user has explicitly approved,
     * each pinned to the content hash it had at approval time). Lives directly under the app data
     * directory so it is created and read before any plugin jar is touched.
     */
    fun getTrustRegistryFile(): File = File(appDataDir, "trusted-plugins.json")

    /**
     * Resolves the directory that stores TLS material (server keystores, CA certificates, metadata)
     * for secure WebSocket (wss) connections, creating it if necessary.
     */
    fun getSslDirectory(): File = File(sslDir).apply {
        if (!exists()) {
            mkdirs()
        }
        // TLS material (including the CA private key) lives here, so restrict the directory to the
        // owning user only (0700 on POSIX; owner-only fallback on non-POSIX filesystems).
        FilePermissions.restrictToOwnerDirectory(this)
    }

    /**
     * True only for a `.jar` file directly inside the managed plugins directory. Paths are compared
     * canonically so `..` segments or symlinked aliases cannot smuggle in a jar from elsewhere. This
     * is the precondition for trusting a jar: the plugins directory is the security boundary, and
     * only files placed there through the explicit install flow may be approved.
     */
    fun isManagedPluginJarPath(jarPath: String): Boolean {
        val file = File(jarPath)
        if (file.extension != "jar") return false
        return try {
            file.canonicalFile.parentFile == File(pluginDir).canonicalFile
        } catch (e: java.io.IOException) {
            false
        }
    }

    fun createAppDataDirectoriesIfNeeded() {
        val appDataDirectory = File(appDataDir)
        if (!appDataDirectory.exists()) {
            appDataDirectory.mkdirs()
        }
        val pluginDirectory = File(pluginDir)
        if (!pluginDirectory.exists()) {
            pluginDirectory.mkdirs()
        }
        val pluginLibsDirectory = File(pluginLibsDir)
        if (!pluginLibsDirectory.exists()) {
            pluginLibsDirectory.mkdirs()
        }
    }

    fun copyJarFileToAppDataDirectory(jarFilePath: String): String {
        val jarFileName = jarFilePath.substringAfterLast('/')
        val destinationPath = "$pluginDir/$jarFileName"

        File(jarFilePath).copyTo(File(destinationPath), overwrite = true)

        return destinationPath
    }

    fun getAllPluginJarFilePaths(): List<String> {
        val pluginDirectory = File(pluginDir)
        return pluginDirectory.listFiles { file -> file.extension == "jar" }?.map { it.absolutePath } ?: emptyList()
    }

    fun getPluginDirectory(): File = File(pluginDir)

    /**
     * Directory holding the external dependency jars that Maven-installed plugins declare in their
     * `META-INF/jetwhale/dependencies.txt` manifest. Kept in a subdirectory of the plugins
     * directory so the jars are not themselves picked up as plugins by [getAllPluginJarFilePaths].
     */
    fun getPluginLibsDirectory(): File = File(pluginLibsDir)

    /**
     * The development-only "dev plugins directory" supplied by a plugin developer via the
     * `jetwhale.devPluginsDir` JVM system property (set by the `runJetWhale` Gradle task).
     *
     * Returns `null` in normal usage so production behaviour is unchanged. When present, the host
     * additionally loads and hot-reloads plugins from this directory, on top of the regular
     * `~/.jetwhale/plugins` directory.
     */
    fun getDevPluginsDir(): String? = System.getProperty(DEV_PLUGINS_DIR_PROPERTY)?.takeIf { it.isNotBlank() }

    /** Returns the absolute paths of every jar currently in the dev plugins directory, if configured. */
    fun getDevPluginJarFilePaths(): List<String> {
        val devDir = getDevPluginsDir() ?: return emptyList()
        val devDirectory = File(devDir)
        return devDirectory.listFiles { file -> file.extension == "jar" }?.map { it.absolutePath } ?: emptyList()
    }

    companion object {
        const val DEV_PLUGINS_DIR_PROPERTY = "jetwhale.devPluginsDir"
    }
}
