package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.MavenCoordinates
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.io.File

@SingleIn(AppScope::class)
@Inject
class MavenArtifactResolver {
    private val httpClient = HttpClient(CIO)

    /**
     * Downloads a JAR file from Maven repository to the specified destination directory.
     * @param coordinates Maven coordinates of the artifact to download
     * @param destinationDir Directory where the JAR file will be saved
     * @return Path to the downloaded JAR file
     * @throws MavenArtifactDownloadException if download fails
     */
    suspend fun downloadJar(coordinates: MavenCoordinates, destinationDir: File): String {
        val jarUrl = coordinates.toJarUrl()
        val jarFileName = "${coordinates.artifactId}-${coordinates.version}.jar"
        val destinationFile = File(destinationDir, jarFileName)

        try {
            val response = httpClient.get(jarUrl)
            if (!response.status.isSuccess()) {
                throw MavenArtifactDownloadException(
                    "Failed to download artifact from $jarUrl. HTTP status: ${response.status}"
                )
            }

            destinationFile.outputStream().use { output ->
                response.bodyAsChannel().toInputStream().use { input ->
                    input.copyTo(output)
                }
            }

            return destinationFile.absolutePath
        } catch (e: MavenArtifactDownloadException) {
            throw e
        } catch (e: Exception) {
            throw MavenArtifactDownloadException(
                "Failed to download artifact $coordinates: ${e.message}",
                e
            )
        }
    }
}

class MavenArtifactDownloadException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
