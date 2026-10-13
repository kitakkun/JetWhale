package com.kitakkun.jetwhale.host.data.update

import com.kitakkun.jetwhale.host.model.HostLaunch
import com.kitakkun.jetwhale.host.model.HostReleaseSource
import com.kitakkun.jetwhale.host.model.HostUpdateFailure
import com.kitakkun.jetwhale.host.model.HostUpdateState
import com.kitakkun.jetwhale.host.model.HostUpdateStatus
import com.kitakkun.jetwhale.host.model.HostVersionInfo
import com.kitakkun.jetwhale.host.model.SetAsideHostVersion
import com.kitakkun.jetwhale.host.release.HostDirectory
import com.kitakkun.jetwhale.host.release.HostPlatformRelease
import com.kitakkun.jetwhale.host.release.HostReleaseMetadata
import com.kitakkun.jetwhale.host.release.HostReleaseMetadataReader
import com.kitakkun.jetwhale.host.release.HostRuntimeRequirements
import com.kitakkun.jetwhale.host.release.HostVersion
import com.kitakkun.jetwhale.host.release.LauncherState
import com.kitakkun.jetwhale.host.release.ReleaseMetadataSignatureVerifier
import com.kitakkun.jetwhale.host.release.hostJarName
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume
import java.io.IOException
import java.nio.channels.UnresolvedAddressException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

internal fun releaseMetadata(versionName: String) = HostReleaseMetadata(
    format = 1,
    version = hostVersion(versionName),
    mainClass = "com.kitakkun.jetwhale.host.MainKt",
    launcherContract = 1,
    runtime = HostRuntimeRequirements(javaFeatureVersion = 21, modules = listOf("java.base")),
    jvmArgs = emptyList(),
    platforms = mapOf(
        PLATFORM to HostPlatformRelease(
            url = jarUrl(versionName),
            size = jarBytes(versionName).size.toLong(),
            sha256 = MessageDigest.getInstance("SHA-256").digest(jarBytes(versionName)).joinToString("") { "%02x".format(it) },
            jvmArgs = emptyList(),
        ),
    ),
)

internal class TestRelease(val tag: String, val json: String, val metadata: HostReleaseMetadata?)

internal const val PLATFORM = "macos-arm64"
internal const val RELEASES_URL = "https://api.github.com/repos/kitakkun/JetWhale/releases?per_page=30"
internal const val ASSET_HOST_URL = "https://objects.example.com/release-asset"

internal fun metadataUrl(tag: String) = "https://github.com/kitakkun/JetWhale/releases/download/$tag/jetwhale-host-$tag.json"

internal fun jarUrl(tag: String) = "https://github.com/kitakkun/JetWhale/releases/download/$tag/${hostJarName(hostVersion(tag), PLATFORM)}"

internal fun jarBytes(versionName: String) = "host jar of $versionName".toByteArray()

internal fun hostVersion(name: String): HostVersion = checkNotNull(HostVersion.parse(name)) { name }
