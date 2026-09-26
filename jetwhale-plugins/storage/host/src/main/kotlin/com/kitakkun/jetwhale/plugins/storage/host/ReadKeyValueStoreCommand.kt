package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueStoreContent

@OptIn(ExperimentalJetWhaleApi::class)
internal class ReadKeyValueStoreCommand(
    private val client: StorageClient,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.readKeyValueStore"
    override val description =
        "Reads every entry of one of the app's key-value stores, with each value's type as the store reports it."

    private val store by string("Name of the store, as listLocations reports it.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String =
        McpJson.encodeToString(KeyValueStoreContent.serializer(), client.readKeyValueStore(arguments[store]))
}
