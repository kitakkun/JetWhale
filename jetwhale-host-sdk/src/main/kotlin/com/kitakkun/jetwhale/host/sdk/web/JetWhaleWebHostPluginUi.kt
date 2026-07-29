package com.kitakkun.jetwhale.host.sdk.web

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi

/**
 * Marks a [JetWhaleHostPluginUi] whose `Content` embeds a [JetWhaleWebView].
 *
 * The host renders a web plugin's `Content` in a real windowed composition so the embedded browser's
 * heavyweight component can attach, instead of the off-screen Compose scene used for pure-Compose
 * plugins. Everything else about the plugin — lifecycle, messaging, storage — is unchanged; implement
 * this in addition to providing `Content`.
 *
 * Trade-offs of the windowed path: the host cannot capture a screenshot of a web plugin, and Compose
 * overlays the host draws may be occluded by the browser surface.
 */
@ExperimentalJetWhaleApi
public interface JetWhaleWebHostPluginUi : JetWhaleHostPluginUi
