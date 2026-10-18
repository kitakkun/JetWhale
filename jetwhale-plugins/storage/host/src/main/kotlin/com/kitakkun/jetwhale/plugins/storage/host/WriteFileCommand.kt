package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.storage.protocol.StorageOperationResult
import kotlin.io.encoding.Base64

@OptIn(ExperimentalJetWhaleApi::class)
internal class WriteFileCommand(
    private val client: StorageClient,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.writeFile"
    override val description =
        "Writes a file into the app's storage, creating it or replacing the one at that path — to seed test data or put a saved state back. The file is replaced only once all of it has arrived. The directory must already exist. An app that keeps the file open (a DataStore, a SQLite database) may keep using what it read before, or write it back, until it restarts."

    private val root by string("Name of the file root, as listLocations reports it.")
    private val path by string(PATH_ARGUMENT_DESCRIPTION)
    private val content by string("The file's content, as text or Base64 according to encoding.")
    private val encoding by stringOrNull("How content is encoded: 'utf-8' (the default) for text, 'base64' for anything else.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val location = fileLocationOf(arguments[root], arguments[path])
        if (location.path.isEmpty()) throw JetWhaleMcpArgumentException("path must name a file below the root")
        val bytes = when (arguments[encoding] ?: "utf-8") {
            "utf-8" -> arguments[content].encodeToByteArray()

            "base64" -> try {
                Base64.decode(arguments[content])
            } catch (e: IllegalArgumentException) {
                throw JetWhaleMcpArgumentException("content is not Base64: ${e.message}")
            }

            else -> throw JetWhaleMcpArgumentException("encoding must be 'utf-8' or 'base64'")
        }
        val error = client.writeWholeFile(
            location = location,
            totalSizeBytes = bytes.size.toLong(),
            readChunk = { offset, size -> bytes.copyOfRange(offset.toInt(), offset.toInt() + size) },
            onProgress = {},
        )
        return McpJson.encodeToString(StorageOperationResult.serializer(), StorageOperationResult(error = error))
    }
}
