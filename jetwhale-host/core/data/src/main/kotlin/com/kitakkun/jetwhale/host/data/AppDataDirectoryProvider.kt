package com.kitakkun.jetwhale.host.data

import com.kitakkun.jetwhale.host.model.AdditionalPluginDirectories
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import okio.Path
import okio.Path.Companion.toPath
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@SingleIn(AppScope::class)
@Inject
class AppDataDirectoryProvider(
    private val additionalPluginDirectories: AdditionalPluginDirectories,
) {
    private val homeDir = System.getProperty("user.home")

    // The app data root. Normally `~/.jetwhale`, but a launch may point it elsewhere via the
    // `jetwhale.appDataDir` system property. The plugin-developer Gradle tasks (`runJetWhale`,
    // `runJetWhaleHot`, `runJetWhaleLocal`) set it to a disposable per-project sandbox under the plugin module's `build/`
    // directory, so trying a plugin never reads or mutates the developer's real installed plugins,
    // settings, plugin-data or trust registry. Every path below is derived from this single root.
    private val appDataDir = System.getProperty(APP_DATA_DIR_PROPERTY)?.takeIf(String::isNotBlank)
        ?: "$homeDir/.jetwhale"
    private val isAppDataDirOverridden = System.getProperty(APP_DATA_DIR_PROPERTY)?.isNotBlank() == true
    private val pluginDir = "$appDataDir/plugins"
    private val pluginLibsDir = "$pluginDir/libs"
    private val pluginStagingDir = "$pluginDir/staging"
    private val dataStoreFilesDir = "$appDataDir/dataStorePreferences"
    private val pluginDataDir = "$appDataDir/plugin-data"
    private val sslDir = "$appDataDir/ssl"

    fun resolveDataStoreFilePath(fileName: String): Path = "$dataStoreFilesDir/$fileName".toPath()

    /**
     * Resolves the data directory of a single plugin, which holds one subdirectory per version (see
     * [resolvePluginVersionDataDir]). Each plugin gets its own directory so plugins cannot reach each
     * other's data. [pluginId] is sanitized first so a crafted id (e.g. one containing path
     * separators or `..`) cannot escape [pluginDataDir].
     */
    fun resolvePluginDataDir(pluginId: String): Path = "$pluginDataDir/${sanitizePathSegment(pluginId)}".toPath()

    /** Resolves the data directory of [version] of [pluginId]; [version] is sanitized like the id. */
    fun resolvePluginVersionDataDir(pluginId: String, version: String): Path = resolvePluginDataDir(pluginId) / sanitizePathSegment(version)

    private fun sanitizePathSegment(segment: String): String {
        val sanitized = buildString {
            for (c in segment) {
                append(if (c.isLetterOrDigit() || c == '.' || c == '-' || c == '_') c else '_')
            }
        }
        // Sanitization is lossy: distinct ids like "a/b" and "a_b" would otherwise collapse into the
        // same directory (breaking isolation and DataStore's single-instance-per-file rule). When any
        // character was replaced, a hash of the original is appended to keep the name unique.
        val hashSuffix = "_" + segment.hashCode().toUInt().toString(16)
        return when {
            sanitized.isEmpty() || sanitized == "." || sanitized == ".." -> "plugin$hashSuffix"
            sanitized != segment -> sanitized + hashSuffix
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
     * Resolves the directory that stores TLS material (server keystores, CA certificates, metadata)
     * for secure WebSocket (wss) connections, creating it if necessary.
     */
    fun getSslDirectory(): File = File(sslDir).apply {
        if (!exists()) {
            mkdirs()
        }
        // TLS material (including the CA private key) lives here, so restrict the directory to the
        // owning user only (0700 on POSIX; owner-only fallback on non-POSIX filesystems).
        FilePermissionsWriter.restrictToOwnerDirectory(this)
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
        } catch (_: IOException) {
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
        val pluginStagingDirectory = File(pluginStagingDir)
        if (!pluginStagingDirectory.exists()) {
            pluginStagingDirectory.mkdirs()
        }
    }

    fun copyJarFileToAppDataDirectory(jarFilePath: String): String {
        val jarFileName = File(jarFilePath).name
        val destination = File(pluginDir, jarFileName)
        // Copied into the staging directory and moved in whole: the plugins directory is watched, and
        // a copy that pauses long enough would be offered half-written.
        val staged = File.createTempFile("$jarFileName.", ".part", File(pluginStagingDir))
        try {
            File(jarFilePath).copyTo(staged, overwrite = true)
            moveStagedJarIntoPluginDirectory(staged, destination)
        } finally {
            staged.delete()
        }
        return destination.path
    }

    /**
     * Moves a complete jar from the staging directory to [destination] in the plugins directory,
     * replacing a jar of the same name. Atomic where the file system supports it, so the watcher sees
     * either the old jar or the new one; a replacing move otherwise.
     */
    fun moveStagedJarIntoPluginDirectory(staged: File, destination: File) {
        try {
            Files.move(staged.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(staged.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun getAllPluginJarFilePaths(): List<String> {
        val pluginDirectory = File(pluginDir)
        return pluginDirectory.listFiles { file -> file.extension == "jar" }?.map(File::getAbsolutePath) ?: emptyList()
    }

    fun getPluginDirectory(): File = File(pluginDir)

    /**
     * Directory holding the external dependency jars that Maven-installed plugins declare in their
     * `META-INF/jetwhale/dependencies.txt` manifest. Kept in a subdirectory of the plugins
     * directory so the jars are not themselves picked up as plugins by [getAllPluginJarFilePaths].
     */
    fun getPluginLibsDirectory(): File = File(pluginLibsDir)

    /**
     * Directory an install downloads a plugin jar into before moving it into the plugins directory,
     * so the jar enters that watched directory complete and only right before it is approved.
     */
    fun getPluginStagingDirectory(): File = File(pluginStagingDir)

    /**
     * The development-only "dev plugins directory" supplied by a plugin developer via the
     * `jetwhale.devPluginsDir` JVM system property (set by the `runJetWhale` Gradle task).
     *
     * Returns `null` in normal usage so production behaviour is unchanged. When present, the host
     * additionally loads and hot-reloads plugins from this directory, on top of the regular managed
     * plugins directory ([getPluginDirectory], which is `~/.jetwhale/plugins` — or, under the dev
     * sandbox, the sandbox root's `plugins` subdirectory).
     */
    fun getDevPluginsDir(): String? = System.getProperty(DEV_PLUGINS_DIR_PROPERTY)?.takeIf(String::isNotBlank)

    /**
     * Absolute paths of every jar in the directories named with `--plugin-dir`, if any.
     *
     * Directories that do not exist, or that cannot be listed, contribute nothing rather than failing
     * the launch: a stale path in a shell alias should not stop the host from starting.
     */
    fun getAdditionalPluginJarFilePaths(): List<String> = additionalPluginDirectories.paths
        .flatMap { path ->
            // listFiles returns null for a missing path or a plain file, but *throws* SecurityException
            // when a directory exists and cannot be read. Both are the same thing from here — one
            // unusable directory — and neither should take the launch down with it.
            runCatching { File(path).listFiles { file -> file.extension == "jar" }?.toList() }
                .getOrNull()
                .orEmpty()
        }
        .map(File::getAbsolutePath)

    /** Returns the absolute paths of every jar currently in the dev plugins directory, if configured. */
    fun getDevPluginJarFilePaths(): List<String> {
        val devDir = getDevPluginsDir() ?: return emptyList()
        val devDirectory = File(devDir)
        return devDirectory.listFiles { file -> file.extension == "jar" }?.map(File::getAbsolutePath) ?: emptyList()
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
