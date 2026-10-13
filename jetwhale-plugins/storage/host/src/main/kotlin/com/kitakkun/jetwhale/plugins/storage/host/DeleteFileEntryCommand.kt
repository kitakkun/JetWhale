package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.storage.protocol.StorageOperationResult

@OptIn(ExperimentalJetWhaleApi::class)
internal class DeleteFileEntryCommand(
    private val client: StorageClient,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.deleteFileEntry"
    override val description =
        "Deletes a file, or a directory with everything in it, from the app's storage — to clear a cache or reset the app's state. It cannot be undone, and a root itself cannot be deleted."

    private val root by string("Name of the file root, as listLocations reports it.")
    private val path by string(PATH_ARGUMENT_DESCRIPTION)

    override suspend fun execute(arguments: JetWhaleMcpArguments): String = McpJson.encodeToString(StorageOperationResult.serializer(), client.delete(fileLocationOf(arguments[root], arguments[path])))
}
