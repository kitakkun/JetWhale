package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.storage.protocol.StorageLocations

@OptIn(ExperimentalJetWhaleApi::class)
internal class ListStorageLocationsCommand(
    private val client: StorageClient,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.listLocations"
    override val description =
        "Lists where the app keeps data: the file roots that can be browsed (each with its absolute path on the device) and the key-value stores that can be read (SharedPreferences files, NSUserDefaults, localStorage). Every other storage tool takes one of these names."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String =
        McpJson.encodeToString(StorageLocations.serializer(), client.locations())
}
