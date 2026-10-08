package com.kitakkun.jetwhale.tools.docsscreenshots

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.sdk.JetWhalePluginStorage
import com.kitakkun.jetwhale.host.sdk.LocalJetWhalePluginStorage
import com.kitakkun.jetwhale.host.ui.JwSurface
import com.kitakkun.jetwhale.host.ui.JwTheme

/**
 * What the host renders a plugin's UI in: the built-in theme for [darkTheme], a surface filling the
 * scene, and [storage] behind `rememberPersistent`.
 */
// A frame around the plugin screen it is given, with no look of its own to preview.
@Suppress("KOTRAIL_COMPOSABLE_WITHOUT_PREVIEW")
@Composable
fun PluginSceneSurface(
    darkTheme: Boolean,
    storage: JetWhalePluginStorage,
    content: @Composable () -> Unit,
) {
    JwTheme(darkTheme = darkTheme) {
        JwSurface(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalJetWhalePluginStorage provides storage, content = content)
        }
    }
}
