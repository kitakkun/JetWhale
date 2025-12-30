package com.kitakkun.jetwhale.debugger.host.data

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

    fun resolveDataStoreFilePath(fileName: String): Path {
        return "$dataStoreFilesDir/$fileName".toPath()
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
}
