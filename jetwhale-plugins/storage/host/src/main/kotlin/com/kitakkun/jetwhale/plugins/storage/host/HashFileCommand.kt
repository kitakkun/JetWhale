package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalJetWhaleApi::class)
internal class HashFileCommand(
    private val client: StorageClient,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.hashFile"
    override val description =
        "Computes the SHA-256 of a whole file in the app's storage, to tell whether two copies match or whether a file changed. The " +
            "whole file is read from the app to compute it, so a large file takes a while."

    private val root by string("Name of the file root, as listLocations reports it.")
    private val path by string(PATH_ARGUMENT_DESCRIPTION)

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val location = fileLocationOf(arguments[root], arguments[path])
        if (location.path.isEmpty()) throw JetWhaleMcpArgumentException("path must name a file below the root")
        val digest = client.sha256Of(location)
        return buildJsonObject {
            digest.sha256Hex?.let { put("sha256", it) }
            digest.error?.let { put("error", it) }
        }.toString()
    }
}
