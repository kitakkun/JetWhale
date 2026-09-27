package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.storage.protocol.DirectoryMeasurement

@OptIn(ExperimentalJetWhaleApi::class)
internal class MeasureDirectoryCommand(
    private val client: StorageClient,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.measureDirectory"
    override val description =
        "Adds up a directory of the app's storage and everything below it: the total size in bytes and how many files and directories " +
            "it holds — how big a cache has grown, say. Symbolic links are counted but not followed. " +
            "A very large tree is cut off, and then " +
            "truncated is true and the totals are a floor."

    private val root by string("Name of the file root, as listLocations reports it.")
    private val path by stringOrNull("$PATH_ARGUMENT_DESCRIPTION Omit for the root itself.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String =
        McpJson.encodeToString(DirectoryMeasurement.serializer(), client.measureDirectory(fileLocationOf(arguments[root], arguments[path])))
}
