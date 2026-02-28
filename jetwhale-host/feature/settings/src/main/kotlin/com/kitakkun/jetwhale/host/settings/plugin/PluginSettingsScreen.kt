package com.kitakkun.jetwhale.host.settings.plugin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.settings.Res
import com.kitakkun.jetwhale.host.settings.SettingsScreenScaffoldPageContentPadding
import com.kitakkun.jetwhale.host.settings.installed_plugins
import org.jetbrains.compose.resources.stringResource

@Composable
fun PluginSettingsScreen(
    uiState: PluginSettingsScreenUiState,
    onClickAddPlugin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(SettingsScreenScaffoldPageContentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.installed_plugins),
            style = MaterialTheme.typography.titleMedium,
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(
                top = 0.dp,
                start = 8.dp,
                end = 8.dp,
                bottom = 8.dp,
            ),
            modifier = Modifier
                .weight(1f)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ),
        ) {
            stickyHeader {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(top = 8.dp),
                ) {
                    Text("id", Modifier.weight(1f))
                    Text("name", Modifier.weight(1f))
                    Text("version", Modifier.width(100.dp))
                }
                HorizontalDivider()
            }
            items(
                items = uiState.plugins,
                key = { plugin -> plugin.id },
            ) { plugin ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = plugin.id, Modifier.weight(1f))
                    Text(text = plugin.name, Modifier.weight(1f))
                    Text(text = plugin.version, Modifier.width(100.dp))
                }
            }
        }
        Button(onClickAddPlugin) {
            Text("Add Plugin")
        }
    }
}
