package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueEntry
import com.kitakkun.jetwhale.plugins.storage.protocol.MAX_FILE_READ_BYTES
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64

/** Enough for a settings file or a JSON cache entry, small enough not to flood the caller's context. */
private const val DEFAULT_READ_BYTES = 64 * 1024

@OptIn(ExperimentalJetWhaleApi::class)
internal class ReadFileCommand(
    private val client: StorageClient,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.readFile"
    override val description =
        "Reads a file from the app's storage. Text comes back as UTF-8 text, anything else as Base64; a Preferences DataStore file (*.preferences_pb) read whole is also decoded into its entries. A large file is read in pages: totalSizeBytes says how big it is, and offset picks up where the last read ended."

    private val root by string("Name of the file root, as listLocations reports it.")
    private val path by string(PATH_ARGUMENT_DESCRIPTION)
    private val offset by longOrNull("Byte offset to start reading at. Defaults to 0.")
    private val maxBytes by intOrNull("How many bytes to read at most. Defaults to $DEFAULT_READ_BYTES; capped at $MAX_FILE_READ_BYTES.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val location = fileLocationOf(arguments[root], arguments[path])
        if (location.path.isEmpty()) throw JetWhaleMcpArgumentException("path must name a file below the root")
        val start = arguments[offset] ?: 0
        val content = client.readFile(location, offset = start, maxBytes = arguments[maxBytes] ?: DEFAULT_READ_BYTES)
        content.error?.let { error -> return buildJsonObject { put("error", error) }.toString() }

        val bytes = Base64.decode(content.contentBase64)
        val file = LoadedFile(location = location, bytes = bytes, totalSizeBytes = content.totalSizeBytes)
        val text = decodeTextOrNull(bytes)
        return buildJsonObject {
            put("totalSizeBytes", content.totalSizeBytes)
            put("offset", start)
            put("bytesRead", bytes.size)
            put("encoding", if (text != null) "utf-8" else "base64")
            put("content", text ?: content.contentBase64)
            if (start == 0L && PreviewFormat.Preferences in previewFormatsOf(file)) {
                try {
                    put("preferences", McpJson.encodeToJsonElement(ListSerializer(KeyValueEntry.serializer()), decodePreferencesDataStore(bytes)))
                } catch (e: IllegalArgumentException) {
                    put("preferencesError", e.message)
                }
            }
        }.toString()
    }
}
