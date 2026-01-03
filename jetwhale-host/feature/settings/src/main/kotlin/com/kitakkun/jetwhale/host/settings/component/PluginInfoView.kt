package com.kitakkun.jetwhale.host.settings.component

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PluginInfoView(
    uiState: PluginInfoUiState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = uiState.name, style = MaterialTheme.typography.bodyLarge)
        Text(text = uiState.id, style = MaterialTheme.typography.bodyMedium)
        Text(text = uiState.version, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview
@Composable
private fun PluginInfoViewPreview() {
    PluginInfoView(
        uiState = PluginInfoUiState(
            id = "",
            name = "",
            version = ""
        )
    )
}
