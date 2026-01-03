package com.kitakkun.jetwhale.host.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.PluginIconResource
import com.kitakkun.jetwhale.host.model.PluginMetaData
import com.kitakkun.jetwhale.host.puzzle_filled
import com.kitakkun.jetwhale.host.puzzle_outlined
import com.kitakkun.jetwhale.host.sidebar_unfold
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.decodeToSvgPainter
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShrunkToolingDrawerView(
    plugins: ImmutableList<PluginMetaData>,
    sessions: ImmutableList<DebugSession>,
    selectedSessionId: String?,
    selectedPluginId: String,
    onClickExpandMenu: () -> Unit,
    onClickSettings: () -> Unit,
    onClickPlugin: (String) -> Unit,
    onClickInfo: () -> Unit,
    onSelectSession: (DebugSession) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(50.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        IconButton(onClick = onClickExpandMenu) {
            Icon(
                painter = painterResource(Res.drawable.sidebar_unfold),
                contentDescription = null,
            )
        }
        Box {
            var expanded by remember { mutableStateOf(false) }
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                tooltip = {
                    if (sessions.isEmpty()) {
                        PlainTooltip {
                            Text("No active sessions")
                        }
                    }
                },
                state = rememberTooltipState(),
            ) {
                IconButton(
                    enabled = sessions.isNotEmpty(),
                    onClick = { expanded = true },
                ) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = null,
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                sessions.forEach { session ->
                    SessionDropdownMenuItem(
                        selected = session.id == selectedSessionId,
                        isActive = session.isActive,
                        displayName = session.displayName,
                        onClick = {
                            onSelectSession(session)
                            expanded = false
                        }
                    )
                }
            }
        }
        IconButton(onClick = onClickSettings) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
            )
        }
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier.weight(1f),
        ) {
            items(plugins) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                    tooltip = {
                        PlainTooltip {
                            Text("${it.name}(${it.id})")
                        }
                    },
                    state = rememberTooltipState(),
                ) {
                    val selected = selectedPluginId == it.id && selectedSessionId != null
                    IconButton(
                        enabled = selectedSessionId != null,
                        onClick = { onClickPlugin(it.id) },
                        colors = if (selected) IconButtonDefaults.filledTonalIconButtonColors() else IconButtonDefaults.iconButtonColors(),
                    ) {
                        Icon(
                            painter = when {
                                selected -> rememberPluginIconSvgPainter(it.activeIconResource)
                                    ?: painterResource(Res.drawable.puzzle_filled)

                                else -> rememberPluginIconSvgPainter(it.inactiveIconResource)
                                    ?: painterResource(Res.drawable.puzzle_outlined)
                            },
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
        HorizontalDivider()
        IconButton(onClick = onClickInfo) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
            )
        }
    }
}

@Composable
fun rememberPluginIconSvgPainter(
    resource: PluginIconResource?
): Painter? {
    if (resource == null) return null
    val density = LocalDensity.current
    return remember(resource) {
        resource.path.openStream().use { it.readBytes().decodeToSvgPainter(density) }
    }
}
