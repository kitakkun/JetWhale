package com.kitakkun.jetwhale.host.model

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.sdk.JetWhaleDebugOperationContext

interface DynamicPluginBridgeProvider {
    @Composable
    fun PluginEntryPoint(content: @Composable (context: JetWhaleDebugOperationContext<String, String>) -> Unit)
}
