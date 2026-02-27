package com.kitakkun.jetwhale.host.drawer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.disable
import com.kitakkun.jetwhale.host.disabled_plugins
import com.kitakkun.jetwhale.host.enable
import com.kitakkun.jetwhale.host.enabled_plugins
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.PluginAvailability
import com.kitakkun.jetwhale.host.no_plugins_installed
import com.kitakkun.jetwhale.host.plugins
import com.kitakkun.jetwhale.host.popout
import com.kitakkun.jetwhale.host.puzzle_outlined
import com.kitakkun.jetwhale.host.unavailable_plugins
import io.github.takahirom.rin.rememberRetained
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
                        verticalArrangement = Arrangement.spacedBy(32.dp),
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
                    var enabledPluginsExpanded by rememberRetained { mutableStateOf(true) }
                    var disabledPluginsExpanded by rememberRetained { mutableStateOf(true) }
                    var unavailablePluginsExpanded by rememberRetained { mutableStateOf(true) }

                    val enabledPlugins = remember(plugins) { plugins.filter { it.pluginAvailability == PluginAvailability.Enabled } }
                    val disabledPlugins = remember(plugins) { plugins.filter { it.pluginAvailability == PluginAvailability.Disabled } }
                    val unavailablePlugins = remember(plugins) { plugins.filter { it.pluginAvailability == PluginAvailability.Unavailable } }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                    ) {
                        if (enabledPlugins.isNotEmpty()) {
                            item {
                                PluginAccordionHeadingView(
                                    title = stringResource(Res.string.enabled_plugins),
                                    expanded = enabledPluginsExpanded,
                                    onExpandToggle = { enabledPluginsExpanded = !enabledPluginsExpanded },
                                    pluginCount = enabledPlugins.size,
                                )
                            }
                            items(
                                items = enabledPlugins,
                                key = { it.id },
                            ) {
                                AnimatedVisibility(enabledPluginsExpanded) {
                                    PluginDrawerItemView(
                                        enabled = true,
                                        name = it.name,
                                        activeIconResource = it.activeIconResource,
                                        inactiveIconResource = it.inactiveIconResource,
                                        selected = it.id == selectedPluginId,
                                        onClick = { onClickPlugin(it) },
                                        popupMenuContent = { dismiss ->
                                            DropdownMenuItem(
                                                text = { Text(stringResource(Res.string.disable)) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.RemoveCircle,
                                                        contentDescription = null,
                                                    )
                                                },
                                                onClick = {
                                                    onSetPluginEnabled(it.id, false)
                                                    dismiss()
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(Res.string.popout)) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowOutward,
                                                        contentDescription = null,
                                                    )
                                                },
                                                onClick = {
                                                    onClickPopout(it)
                                                    dismiss()
                                                },
                                            )
                                        },
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        }
                        if (disabledPlugins.isNotEmpty()) {
                            item {
                                PluginAccordionHeadingView(
                                    title = stringResource(Res.string.disabled_plugins),
                                    expanded = disabledPluginsExpanded,
                                    onExpandToggle = { disabledPluginsExpanded = !disabledPluginsExpanded },
                                    pluginCount = disabledPlugins.size,
                                )
                            }
                            items(
                                items = disabledPlugins,
                                key = { it.id },
                            ) {
                                AnimatedVisibility(disabledPluginsExpanded) {
                                    PluginDrawerItemView(
                                        enabled = false,
                                        name = it.name,
                                        activeIconResource = it.activeIconResource,
                                        inactiveIconResource = it.inactiveIconResource,
                                        selected = false,
                                        onClick = {
                                            // do nothing
                                        },
                                        popupMenuContent = { dismiss ->
                                            DropdownMenuItem(
                                                text = { Text(stringResource(Res.string.enable)) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.AddCircle,
                                                        contentDescription = null,
                                                    )
                                                },
                                                onClick = {
                                                    onSetPluginEnabled(it.id, true)
                                                    dismiss()
                                                },
                                            )
                                        },
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        }
                        if (unavailablePlugins.isNotEmpty()) {
                            item {
                                PluginAccordionHeadingView(
                                    title = stringResource(Res.string.unavailable_plugins),
                                    expanded = unavailablePluginsExpanded,
                                    onExpandToggle = { unavailablePluginsExpanded = !unavailablePluginsExpanded },
                                    pluginCount = unavailablePlugins.size,
                                )
                            }
                        }
                        items(
                            items = unavailablePlugins,
                            key = { it.id },
                        ) {
                            AnimatedVisibility(unavailablePluginsExpanded) {
                                PluginDrawerItemView(
                                    enabled = false,
                                    name = it.name,
                                    activeIconResource = it.activeIconResource,
                                    inactiveIconResource = it.inactiveIconResource,
                                    selected = false,
                                    onClick = {
                                        // do nothing
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
