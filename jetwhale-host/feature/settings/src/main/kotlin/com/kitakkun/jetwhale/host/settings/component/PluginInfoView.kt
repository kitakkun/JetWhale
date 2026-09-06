package com.kitakkun.jetwhale.host.settings.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwTheme

@Composable
fun PluginInfoView(
    uiState: PluginInfoUiState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium),
    ) {
        Text(text = uiState.name, style = JwTheme.textStyles.body)
        Text(text = uiState.id, style = JwTheme.textStyles.body)
        Text(text = uiState.version, style = JwTheme.textStyles.body)
    }
}

@Preview
@Composable
private fun PluginInfoViewPreview() {
    PluginInfoView(
        uiState = PluginInfoUiState(
            id = "",
            name = "",
            version = "",
        ),
    )
}
