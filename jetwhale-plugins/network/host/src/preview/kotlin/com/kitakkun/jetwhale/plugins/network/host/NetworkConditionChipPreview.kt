package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.ui.JwTheme

@Preview
@Composable
private fun NetworkConditionChipPreview() {
    JwTheme(darkTheme = false) {
        NetworkConditionChip()
    }
}
