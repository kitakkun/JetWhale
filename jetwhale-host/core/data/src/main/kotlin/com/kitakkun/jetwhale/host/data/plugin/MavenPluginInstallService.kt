package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.MavenCoordinates
import com.kitakkun.jetwhale.host.model.PluginInstallProgress
import com.kitakkun.jetwhale.host.model.PluginInstallProgressRepository
import com.kitakkun.jetwhale.host.model.PluginTrustService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Installs a plugin from a Maven repository: downloads the plugin jar and the external
 * dependencies it declares, then approves and loads it. Shared by the manual Install-from-Maven
 * flow and the official-plugin catalog.
 */
@SingleIn(AppScope::class)
@Inject
class MavenPluginInstallService(
    private val appDataDirectoryProvider: AppDataDirectoryProvider,
    private val mavenArtifactResolver: MavenArtifactResolver,
    private val pluginTrustService: PluginTrustService,
    private val pluginInstallProgressRepository: PluginInstallProgressRepository,
) {
    /**
     * Attempts [candidates] in order and stops at the first success. Intermediate failures are
     * swallowed (e.g. a release artifact not published yet, before its snapshot fallback); only
     * the last candidate's failure is thrown.
     */
    suspend fun installFirstAvailable(candidates: List<MavenCoordinates>) {
        require(candidates.isNotEmpty()) { "No install candidates given" }
        appDataDirectoryProvider.createAppDataDirectoriesIfNeeded()
        try {
            var lastError: PluginInstallationException? = null
            for (coordinates in candidates) {
                try {
                    install(coordinates)
                    return
                } catch (e: PluginInstallationException) {
                    lastError = e
                }
            }
            throw checkNotNull(lastError) { "Every install candidate failed without reporting an error" }
        } finally {
            pluginInstallProgressRepository.update(null)
        }
    }

    private suspend fun install(coordinates: MavenCoordinates) {
        pluginInstallProgressRepository.update(PluginInstallProgress.DownloadingPlugin)
        // Downloaded outside the plugins directory and moved in only once complete: the directory is
        // watched, and a jar sitting there unapproved while its dependencies download would be offered
        // to the user as a jar that appeared by other means.
        val stagedJar = try {
            File(mavenArtifactResolver.downloadJar(coordinates, appDataDirectoryProvider.getPluginStagingDirectory()))
        } catch (e: Exception) {
            throw PluginInstallationException("Failed to download plugin $coordinates: ${e.message}", e)
        }
        try {
            downloadDeclaredDependencies(stagedJar, coordinates)
        } catch (e: Exception) {
            // A jar of the same name already installed stays as it was.
            stagedJar.delete()
            throw PluginInstallationException("Failed to load plugin from $coordinates: ${e.message}", e)
        }
        val installedJar = File(appDataDirectoryProvider.getPluginDirectory(), stagedJar.name)
        try {
            pluginInstallProgressRepository.update(PluginInstallProgress.LoadingPlugin)
            Files.move(stagedJar.toPath(), installedJar.toPath(), StandardCopyOption.ATOMIC_MOVE)
            // Requesting an install by coordinates is the user's explicit consent, exactly like the
            // file picker: approve (pin the content hash) and load.
            pluginTrustService.trustAndLoad(installedJar.absolutePath)
        } catch (e: Exception) {
            stagedJar.delete()
            installedJar.delete()
            throw PluginInstallationException("Failed to load plugin from $coordinates: ${e.message}", e)
        }
    }

    /**
     * Downloads every external dependency the plugin jar declares in its dependency manifest into
     * the plugin libs directory, skipping jars that are already present (dependencies are shared
     * across plugins by coordinates, and released artifacts are immutable; snapshots are
     * overwritable and always re-downloaded).
     *
     * Each dependency is fetched from the repository the plugin itself came from first, falling
     * back to Maven Central: a plugin in a custom repository may keep its own modules there while
     * depending on public libraries.
     */
    private suspend fun downloadDeclaredDependencies(pluginJar: File, pluginCoordinates: MavenCoordinates) {
        val libsDir = appDataDirectoryProvider.getPluginLibsDirectory()
        val dependencies = PluginDependencyManifest.readFrom(pluginJar)
        dependencies.forEachIndexed { index, dependency ->
            pluginInstallProgressRepository.update(
                PluginInstallProgress.DownloadingDependencies(completed = index, total = dependencies.size),
            )
            if (!dependency.isSnapshot && File(libsDir, dependency.jarFileName()).exists()) return@forEachIndexed
            try {
                mavenArtifactResolver.downloadJar(dependency.copy(repositoryUrl = pluginCoordinates.repositoryUrl), libsDir)
            } catch (e: MavenArtifactDownloadException) {
                if (pluginCoordinates.repositoryUrl == MavenCoordinates.MAVEN_CENTRAL_URL) throw e
                mavenArtifactResolver.downloadJar(dependency.copy(repositoryUrl = MavenCoordinates.MAVEN_CENTRAL_URL), libsDir)
            }
        }
    }
}

class PluginInstallationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
