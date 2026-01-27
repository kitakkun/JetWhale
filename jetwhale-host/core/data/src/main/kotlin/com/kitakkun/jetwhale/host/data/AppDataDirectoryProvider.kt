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
    private val dataStoreFilesDir = "$appDataDir/dataStorePreferences"

    fun resolveDataStoreFilePath(fileName: String): Path = "$dataStoreFilesDir/$fileName".toPath()

    fun getAppDataPath(): String = "~/.jetwhale"

    fun createAppDataDirectoriesIfNeeded() {
        val appDataDirectory = File(appDataDir)
        if (!appDataDirectory.exists()) {
            appDataDirectory.mkdirs()
        }
        val pluginDirectory = File(pluginDir)
        if (!pluginDirectory.exists()) {
            pluginDirectory.mkdirs()
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

    fun getPluginDirectory(): File {
        return File(pluginDir)
    }

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
=======
    fun getPluginDirectory(): File {
        return File(pluginDir)
>>>>>>> 6c7db1b (feat: add plugin installation from Maven repository)
    }
}
