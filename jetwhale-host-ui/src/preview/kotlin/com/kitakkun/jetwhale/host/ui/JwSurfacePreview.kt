package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwSurfacePreview() {
    JwTheme(darkTheme = false) {
        JwSurface(shape = JwShapes.medium, border = BorderStroke(JwMetrics.borderWidth, JwTheme.colors.border)) {
            JwText(text = "A painted region")
        }
    }
}
