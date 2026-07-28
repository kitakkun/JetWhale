package com.kitakkun.jetwhale.host.model

/**
 * Installs a plugin from the [OfficialPluginCatalog].
 *
 * Deliberately narrower than the install-by-coordinates path: callers name a catalog entry, never
 * arbitrary [MavenCoordinates]. Installing a plugin loads code into the host process, so the MCP
 * server — which anything on the machine can talk to — is only given this narrow door.
 */
interface OfficialPluginInstallService {
    suspend fun install(plugin: OfficialPlugin)
}
