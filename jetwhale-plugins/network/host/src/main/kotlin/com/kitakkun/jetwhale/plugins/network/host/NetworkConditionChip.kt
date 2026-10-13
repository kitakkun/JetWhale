package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwTagStyle
import com.kitakkun.jetwhale.host.ui.JwTone

@Composable
internal fun NetworkConditionChip() {
    JwTag(text = "NET", tone = JwTone.Warning, style = JwTagStyle.Tinted)
}
