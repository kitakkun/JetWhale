package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.MavenCoordinates
import com.kitakkun.jetwhale.host.model.PluginInstallFromMavenMutationKey
import com.kitakkun.jetwhale.host.model.PluginInstallProgress
import com.kitakkun.jetwhale.host.model.PluginInstallProgressRepository
import com.kitakkun.jetwhale.host.model.PluginTrustService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.MutationId
import soil.query.buildMutationKey
import java.io.File

@Inject
@ContributesBinding(AppScope::class)
class DefaultPluginInstallFromMavenMutationKey(
    private val pluginTrustService: PluginTrustService,
    private val appDataDirectoryProvider: AppDataDirectoryProvider,
    private val mavenArtifactResolver: MavenArtifactResolver,
    private val pluginInstallProgressRepository: PluginInstallProgressRepository,
) : PluginInstallFromMavenMutationKey by buildMutationKey(
    id = MutationId("pluginInstallFromMaven"),
    mutate = { candidates: List<MavenCoordinates> ->
        require(candidates.isNotEmpty()) { "No install candidates given" }
        appDataDirectoryProvider.createAppDataDirectoriesIfNeeded()
        try {
            var lastError: Exception? = null
            for (coordinates in candidates) {
                try {
                    installFrom(
                        coordinates = coordinates,
                        pluginDir = appDataDirectoryProvider.getPluginDirectory(),
                        libsDir = appDataDirectoryProvider.getPluginLibsDirectory(),
                        resolver = mavenArtifactResolver,
                        pluginTrustService = pluginTrustService,
                        onProgress = pluginInstallProgressRepository::update,
                    )
                    lastError = null
                    break
                } catch (e: PluginInstallationException) {
                    lastError = e
                }
            }
            lastError?.let { throw it }
        } finally {
            pluginInstallProgressRepository.update(null)
        }
    },
)

/** Downloads [coordinates] (plugin jar + declared dependencies), then approves and loads it. */
private suspend fun installFrom(
    coordinates: MavenCoordinates,
    pluginDir: File,
    libsDir: File,
    resolver: MavenArtifactResolver,
    pluginTrustService: PluginTrustService,
    onProgress: (PluginInstallProgress) -> Unit,
) {
    onProgress(PluginInstallProgress.DownloadingPlugin)
    val downloadedJarPath = try {
        resolver.downloadJar(coordinates, pluginDir)
    } catch (e: Exception) {
        throw PluginInstallationException("Failed to download plugin $coordinates: ${'$'}{e.message}", e)
    }
    try {
        downloadDeclaredDependencies(
            pluginJar = File(downloadedJarPath),
            pluginCoordinates = coordinates,
            libsDir = libsDir,
            resolver = resolver,
            onProgress = onProgress,
        )
        onProgress(PluginInstallProgress.LoadingPlugin)
        // Requesting an install by coordinates is the user's explicit consent, exactly like the
        // file picker: approve (pin the content hash) and load.
        pluginTrustService.trustAndLoad(downloadedJarPath)
    } catch (e: Exception) {
        File(downloadedJarPath).delete()
        throw PluginInstallationException("Failed to load plugin from $coordinates: ${'$'}{e.message}", e)
    }
}

/**
 * Downloads every external dependency the plugin jar declares in its dependency manifest into
 * [libsDir], skipping jars that are already present (dependencies are shared across plugins by
 * coordinates, and released artifacts are immutable).
 *
 * Each dependency is fetched from the repository the plugin itself came from first, falling back to
 * Maven Central: a plugin in a custom repository may keep its own modules there while depending on
 * public libraries.
 */
private suspend fun downloadDeclaredDependencies(
    pluginJar: File,
    pluginCoordinates: MavenCoordinates,
    libsDir: File,
    resolver: MavenArtifactResolver,
    onProgress: (PluginInstallProgress) -> Unit,
) {
    val dependencies = PluginDependencyManifest.readFrom(pluginJar)
    dependencies.forEachIndexed { index, dependency ->
        onProgress(PluginInstallProgress.DownloadingDependencies(completed = index, total = dependencies.size))
        // Released artifacts are immutable, so an already-downloaded jar can be reused; snapshots
        // are overwritable and must be re-downloaded to pick up the current build.
        if (!dependency.isSnapshot && File(libsDir, dependency.jarFileName()).exists()) return@forEachIndexed
        try {
            resolver.downloadJar(dependency.copy(repositoryUrl = pluginCoordinates.repositoryUrl), libsDir)
        } catch (e: MavenArtifactDownloadException) {
            if (pluginCoordinates.repositoryUrl == MavenCoordinates.MAVEN_CENTRAL_URL) throw e
            resolver.downloadJar(dependency.copy(repositoryUrl = MavenCoordinates.MAVEN_CENTRAL_URL), libsDir)
        }
    }
}

class PluginInstallationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
