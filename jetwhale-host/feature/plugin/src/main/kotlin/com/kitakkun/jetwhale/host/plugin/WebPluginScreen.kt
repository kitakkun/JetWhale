package com.kitakkun.jetwhale.host.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.kitakkun.jetwhale.host.model.DynamicPluginBridgeProvider
import com.kitakkun.jetwhale.host.sdk.InternalJetWhaleHostApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.LocalIsScreenshotCapture
import com.kitakkun.jetwhale.host.sdk.LocalJetWhalePluginStorage

/**
 * Renders a web plugin's `Content` directly in the host's windowed composition.
 *
 * Pure-Compose plugins render into an off-screen [androidx.compose.ui.scene.ComposeScene] drawn onto
 * a Canvas, but a web plugin embeds a heavyweight browser component (via `SwingPanel`) that can only
 * attach to a real window — so its `Content` is composed here instead. It gets the same host
 * environment the off-screen path provides (`DefaultPluginComposeSceneService`): the plugin's own
 * storage plus the bridge-provider entry point (theme, language, Soil).
 *
 * [LocalIsScreenshotCapture] is always `false`: a windowed browser cannot be rendered off-screen for
 * a screenshot, so this path never participates in host screenshot capture.
 */
@OptIn(InternalJetWhaleHostApi::class)
@Composable
internal fun WebPluginScreen(
    instance: JetWhaleHostPlugin,
    bridgeProvider: DynamicPluginBridgeProvider,
) {
    CompositionLocalProvider(
        LocalJetWhalePluginStorage provides instance.boundStorageForRuntime(),
        LocalIsScreenshotCapture provides false,
    ) {
        bridgeProvider.PluginEntryPoint {
            (instance as JetWhaleHostPluginUi).Content()
        }
    }
}
