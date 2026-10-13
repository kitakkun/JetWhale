package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.storage.protocol.StorageOperationResult

@OptIn(ExperimentalJetWhaleApi::class)
internal class RemoveKeyValueCommand(
    private val client: StorageClient,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.removeKeyValue"
    override val description =
        "Removes one entry from one of the app's key-value stores, e.g. to reset a flag. Removing a key the store does not have is not an error."

    private val store by string("Name of the store, as listLocations reports it.")
    private val key by string("The key to remove.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String = McpJson.encodeToString(StorageOperationResult.serializer(), client.removeKeyValue(storeName = arguments[store], key = arguments[key]))
}
