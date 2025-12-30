package com.kitakkun.jetwhale.host.model

import androidx.compose.runtime.Composable

interface DynamicPluginBridgeProvider {
    @Composable
    fun PluginEntryPoint(content: @Composable () -> Unit)
}
