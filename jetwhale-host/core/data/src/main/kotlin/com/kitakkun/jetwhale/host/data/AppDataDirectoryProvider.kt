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

    // The app data root. Normally `~/.jetwhale`, but a launch may point it elsewhere via the
    // `jetwhale.appDataDir` system property. The plugin-developer Gradle tasks (`runJetWhale`,
    // `runJetWhaleHot`, `runJetWhaleLocal`) set it to a disposable per-project sandbox under the plugin module's `build/`
    // directory, so trying a plugin never reads or mutates the developer's real installed plugins,
    // settings, plugin-data or trust registry. Every path below is derived from this single root.
    private val appDataDir = System.getProperty(APP_DATA_DIR_PROPERTY)?.takeIf { it.isNotBlank() }
        ?: "$homeDir/.jetwhale"
    private val isAppDataDirOverridden = System.getProperty(APP_DATA_DIR_PROPERTY)?.isNotBlank() == true
    private val pluginDir = "$appDataDir/plugins"
    private val pluginLibsDir = "$pluginDir/libs"
    private val dataStoreFilesDir = "$appDataDir/dataStorePreferences"
    private val pluginDataDir = "$appDataDir/plugin-data"

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

    // For display (diagnostics/settings). Shows the literal sandbox path when overridden so a developer
    // can see they are running against the isolated directory, and the tilde-abbreviated `~/.jetwhale`
    // otherwise.
    fun getAppDataPath(): String = if (isAppDataDirOverridden) appDataDir else "~/.jetwhale"

    /**
     * The file backing the plugin trust registry (the list of jars the user has explicitly approved,
     * each pinned to the content hash it had at approval time). Lives directly under the app data
     * directory so it is created and read before any plugin jar is touched.
     */
    fun getTrustRegistryFile(): File = File(appDataDir, "trusted-plugins.json")

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
     * additionally loads and hot-reloads plugins from this directory, on top of the regular managed
     * plugins directory ([getPluginDirectory], which is `~/.jetwhale/plugins` — or, under the dev
     * sandbox, the sandbox root's `plugins` subdirectory).
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

        /**
         * JVM system property overriding the app data root (normally `~/.jetwhale`). Set by the
         * plugin-developer Gradle tasks to an isolated per-project sandbox directory.
         */
        const val APP_DATA_DIR_PROPERTY = "jetwhale.appDataDir"
    }
}
