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
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.puzzle_outlined
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.painterResource

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
                        text = "Plugins",
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
                            text = "No plugins installed.",
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        item {
                            Text(
                                text = "Enabled Plugins",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        items(plugins.filter { it.enabledForCurrentSession }) {
                            PluginDrawerItemView(
                                name = it.name,
                                enabled = true,
                                activeIconResource = it.activeIconResource,
                                inactiveIconResource = it.inactiveIconResource,
                                selected = it.id == selectedPluginId,
                                onClick = { onClickPlugin(it) },
                                onClickPopout = { onClickPopout(it) }
                            )
                        }
                        item {
                            Text(
                                text = "Disabled Plugins",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        items(plugins.filter { !it.enabledForCurrentSession }) {
                            PluginDrawerItemView(
                                name = it.name,
                                enabled = false,
                                activeIconResource = it.activeIconResource,
                                inactiveIconResource = it.inactiveIconResource,
                                selected = false, // Disabled plugins cannot be selected
                                onClick = {
                                    // No-op for disabled plugins
                                },
                                onClickPopout = {
                                    // No-op for disabled plugins
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
