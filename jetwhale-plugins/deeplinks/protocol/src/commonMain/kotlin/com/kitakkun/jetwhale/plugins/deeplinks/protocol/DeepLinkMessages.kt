package com.kitakkun.jetwhale.plugins.deeplinks.protocol

import com.kitakkun.jetwhale.protocol.messaging.JetWhaleRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The pluginId shared by the Deep Links agent and host plugins. */
const val DEEP_LINKS_PLUGIN_ID: String = "com.kitakkun.jetwhale.deeplinks"

/** Asks the agent for the deep links the app declares and the example links it registered. */
@SerialName("deeplinks/get_catalog")
@Serializable
data object GetDeepLinkCatalog : JetWhaleRequest<DeepLinkCatalog>

/**
 * Reply to [GetDeepLinkCatalog].
 *
 * @property declared What the platform says the app handles: Android intent filters, iOS URL types.
 * @property templates Example links the app registered, with `{name}` placeholders for their parameters.
 * @property canOpen Whether the agent can open a link on this platform at all.
 * @property notes What discovery could not see on this platform, for the host to show as is.
 */
@SerialName("deeplinks/catalog")
@Serializable
data class DeepLinkCatalog(
    val declared: List<DeclaredDeepLink>,
    val templates: List<DeepLinkTemplate>,
    val canOpen: Boolean,
    val notes: List<String>,
)

/** Opens [url] in the app, the way the platform would if the link were followed from outside. */
@SerialName("deeplinks/open")
@Serializable
data class OpenDeepLink(val url: String) : JetWhaleRequest<DeepLinkOpenResult>

/**
 * Reply to [OpenDeepLink].
 *
 * @property handledBy The screens the platform routed the link to (Android activity names), when it says.
 * @property error Why the link was not opened; null when [opened].
 */
@SerialName("deeplinks/open_result")
@Serializable
data class DeepLinkOpenResult(
    val opened: Boolean,
    val handledBy: List<String>,
    val error: String?,
)
