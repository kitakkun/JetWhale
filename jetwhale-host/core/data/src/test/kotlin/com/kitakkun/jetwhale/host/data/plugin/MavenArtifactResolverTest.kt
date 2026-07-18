package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.MavenCoordinates
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class MavenArtifactResolverTest {
    private val snapshotCoordinates = MavenCoordinates(
        groupId = "com.example",
        artifactId = "my-plugin",
        version = "1.0.0-SNAPSHOT",
        repositoryUrl = "https://example.com/snapshots",
    )

    private fun resolverRespondingMetadata(metadata: String?): MavenArtifactResolver = MavenArtifactResolver(
        MockEngine { request ->
            if (request.url.toString().endsWith("/maven-metadata.xml") && metadata != null) {
                respond(metadata)
            } else {
                respondError(HttpStatusCode.NotFound)
            }
        },
    )

    @Test
    fun `snapshot metadata produces a timestamped jar url`() = runBlocking {
        val resolver = resolverRespondingMetadata(
            """
            <metadata>
              <versioning>
                <snapshot>
                  <timestamp>20260718.103017</timestamp>
                  <buildNumber>1</buildNumber>
                </snapshot>
              </versioning>
            </metadata>
            """.trimIndent(),
        )
        assertEquals(
            "https://example.com/snapshots/com/example/my-plugin/1.0.0-SNAPSHOT/my-plugin-1.0.0-20260718.103017-1.jar",
            resolver.resolveJarUrl(snapshotCoordinates),
        )
    }

    @Test
    fun `missing metadata falls back to the literal snapshot jar url`() = runBlocking {
        val resolver = resolverRespondingMetadata(null)
        assertEquals(
            "https://example.com/snapshots/com/example/my-plugin/1.0.0-SNAPSHOT/my-plugin-1.0.0-SNAPSHOT.jar",
            resolver.resolveJarUrl(snapshotCoordinates),
        )
    }

    @Test
    fun `incomplete metadata falls back to the literal snapshot jar url`() = runBlocking {
        val resolver = resolverRespondingMetadata("<metadata><versioning/></metadata>")
        assertEquals(
            "https://example.com/snapshots/com/example/my-plugin/1.0.0-SNAPSHOT/my-plugin-1.0.0-SNAPSHOT.jar",
            resolver.resolveJarUrl(snapshotCoordinates),
        )
    }

    @Test
    fun `release versions use the literal jar url without fetching metadata`() = runBlocking {
        val resolver = MavenArtifactResolver(
            MockEngine { error("no request expected for release versions") },
        )
        assertEquals(
            "https://repo1.maven.org/maven2/com/example/my-plugin/1.0.0/my-plugin-1.0.0.jar",
            resolver.resolveJarUrl(MavenCoordinates("com.example", "my-plugin", "1.0.0")),
        )
    }
}
