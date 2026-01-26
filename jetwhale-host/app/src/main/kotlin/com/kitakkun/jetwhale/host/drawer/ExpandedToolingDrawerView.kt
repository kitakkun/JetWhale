package com.kitakkun.jetwhale.host.drawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.disabled_plugins
import com.kitakkun.jetwhale.host.enabled_plugins
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.PluginAvailability
import com.kitakkun.jetwhale.host.no_plugins_installed
import com.kitakkun.jetwhale.host.plugins
import com.kitakkun.jetwhale.host.puzzle_outlined
import com.kitakkun.jetwhale.host.unavailable_plugins
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandedToolingDrawerView(
    selectedPluginId: String,
    plugins: ImmutableList<DrawerPluginItemUiState>,
    selectedSession: DebugSession?,
    sessions: ImmutableList<DebugSession>,
    onClickShrinkDrawer: () -> Unit,
    onClickSettings: () -> Unit,
    onClickPlugin: (DrawerPluginItemUiState) -> Unit,
    onSelectSession: (DebugSession) -> Unit,
    onClickPopout: (DrawerPluginItemUiState) -> Unit,
    onSetPluginEnabled: (pluginId: String, enabled: Boolean) -> Unit,
) {
    ModalDrawerSheet {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onClickShrinkDrawer) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                )
            }
            IconButton(onClick = onClickSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                )
            }
        }
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SessionSelectorView(
                selectedSession = selectedSession,
                sessions = sessions,
                onSelectSession = onSelectSession,
            )
            HorizontalDivider()
            when {
                plugins.isEmpty() -> {
                    Text(
                        text = stringResource(Res.string.plugins),
                        modifier = Modifier.padding(16.dp),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.puzzle_outlined),
                            contentDescription = null,
                        )
                        Text(
                            text = stringResource(Res.string.no_plugins_installed),
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        item {
                            Text(
                                text = stringResource(Res.string.enabled_plugins),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        items(plugins.filter { it.pluginAvailability == PluginAvailability.Enabled }) {
                            PluginDrawerItemView(
                                name = it.name,
                                availability = it.pluginAvailability,
                                activeIconResource = it.activeIconResource,
                                inactiveIconResource = it.inactiveIconResource,
                                selected = it.id == selectedPluginId,
                                onClickEnable = { },
                                onClickDisable = { onSetPluginEnabled(it.id, false) },
                                onClick = { onClickPlugin(it) },
                                onClickPopout = { onClickPopout(it) }
                            )
                        }
                        item {
                            Text(
                                text = stringResource(Res.string.disabled_plugins),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        items(plugins.filter { it.pluginAvailability == PluginAvailability.Disabled }) {
                            PluginDrawerItemView(
                                name = it.name,
                                availability = it.pluginAvailability,
                                activeIconResource = it.activeIconResource,
                                inactiveIconResource = it.inactiveIconResource,
                                selected = false,
                                onClickEnable = { onSetPluginEnabled(it.id, true) },
                                onClickDisable = { },
                                onClick = { },
                                onClickPopout = { }
                            )
                        }
                        item {
                            Text(
                                text = stringResource(Res.string.unavailable_plugins),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        items(plugins.filter { it.pluginAvailability == PluginAvailability.Unavailable }) {
                            PluginDrawerItemView(
                                name = it.name,
                                availability = it.pluginAvailability,
                                activeIconResource = it.activeIconResource,
                                inactiveIconResource = it.inactiveIconResource,
                                selected = false,
                                onClickEnable = { },
                                onClickDisable = { },
                                onClick = { },
                                onClickPopout = { }
                            )
                        }
                    }
                }
            }
        }
    }
}
