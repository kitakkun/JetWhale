package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.storage.protocol.DirectoryListing

@OptIn(ExperimentalJetWhaleApi::class)
internal class ListDirectoryCommand(
    private val client: StorageClient,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.listDirectory"
    override val description =
        "Lists a directory of the app's storage. Each entry has its name, whether it is a directory, its size in bytes, when it was " +
            "last modified and created (epoch milliseconds; creation is null where the platform does not report it), whether the app can " +
            "read and write it, and, for a symbolic link, isSymbolicLink with the linkTarget it points to. Directories come first."

    private val root by string("Name of the file root, as listLocations reports it.")
    private val path by stringOrNull("$PATH_ARGUMENT_DESCRIPTION Omit for the root itself.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String =
        McpJson.encodeToString(DirectoryListing.serializer(), client.listDirectory(fileLocationOf(arguments[root], arguments[path])))
}
