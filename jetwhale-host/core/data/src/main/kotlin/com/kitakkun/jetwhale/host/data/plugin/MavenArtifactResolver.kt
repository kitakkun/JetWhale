package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.MavenCoordinates
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.io.File

@SingleIn(AppScope::class)
@Inject
class MavenArtifactResolver {
    private val httpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
    }

    /**
     * Downloads a JAR file from Maven repository to the specified destination directory.
     * @param coordinates Maven coordinates of the artifact to download
     * @param destinationDir Directory where the JAR file will be saved
     * @return Path to the downloaded JAR file
     * @throws MavenArtifactDownloadException if download fails
     */
    suspend fun downloadJar(coordinates: MavenCoordinates, destinationDir: File): String {
        val jarUrl = resolveJarUrl(coordinates)
        val destinationFile = File(destinationDir, coordinates.jarFileName())

        try {
            val response = httpClient.get(jarUrl)
            if (!response.status.isSuccess()) {
                throw MavenArtifactDownloadException(
                    "Failed to download artifact from $jarUrl. HTTP status: ${response.status}",
                )
            }

            destinationFile.outputStream().use { output ->
                response.bodyAsChannel().toInputStream().use { input ->
                    input.copyTo(output)
                }
            }

            return destinationFile.absolutePath
        } catch (e: MavenArtifactDownloadException) {
            destinationFile.delete()
            throw e
        } catch (e: Exception) {
            destinationFile.delete()
            throw MavenArtifactDownloadException(
                "Failed to download artifact $coordinates: ${e.message}",
                e,
            )
        }
    }

    /**
     * Maven repositories store snapshot jars under timestamped file names (e.g.
     * `foo-1.0.0-20260718.103017-1.jar`), so for `-SNAPSHOT` versions the current build is resolved
     * through the version directory's `maven-metadata.xml`. Falls back to the literal `-SNAPSHOT`
     * file name when the metadata is missing or incomplete (some repositories serve it directly).
     */
    private suspend fun resolveJarUrl(coordinates: MavenCoordinates): String {
        if (!coordinates.isSnapshot) return coordinates.toJarUrl()

        val metadataResponse = httpClient.get("${coordinates.toVersionDirectoryUrl()}/maven-metadata.xml")
        if (!metadataResponse.status.isSuccess()) return coordinates.toJarUrl()

        val metadata = metadataResponse.bodyAsText()
        val timestamp = extractXmlTagValue(metadata, "timestamp") ?: return coordinates.toJarUrl()
        val buildNumber = extractXmlTagValue(metadata, "buildNumber") ?: return coordinates.toJarUrl()
        val timestampedVersion = coordinates.version.removeSuffix("SNAPSHOT") + "$timestamp-$buildNumber"
        return coordinates.toSnapshotJarUrl(timestampedVersion)
    }

    private fun extractXmlTagValue(text: String, tag: String): String? = Regex("<$tag>\\s*([^<]+?)\\s*</$tag>").find(text)?.groupValues?.get(1)
}

class MavenArtifactDownloadException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
