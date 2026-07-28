package com.kitakkun.jetwhale.host.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.ai_agent_operating
import com.kitakkun.jetwhale.host.model.PluginIconResource
import com.kitakkun.jetwhale.host.plugin_exposes_mcp_tools
import com.kitakkun.jetwhale.host.puzzle_filled
import com.kitakkun.jetwhale.host.puzzle_outlined
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun PluginDrawerItemView(
    enabled: Boolean,
    name: String,
    selected: Boolean,
    underAiControl: Boolean,
    exposesMcpTools: Boolean,
    activeIconResource: PluginIconResource?,
    inactiveIconResource: PluginIconResource?,
    onClick: () -> Unit,
    popupMenuContent: (@Composable ColumnScope.(dismiss: () -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
    ) {
        NavigationDrawerItem(
            label = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(name)
                    if (exposesMcpTools) {
                        // Static marker: this plugin publishes MCP tools an agent can call, whether
                        // or not anything is happening right now.
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = stringResource(Res.string.plugin_exposes_mcp_tools),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    if (underAiControl) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = stringResource(Res.string.ai_agent_operating),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(16.dp)
                                .alpha(aiActivityPulseAlpha(operating = true)),
                        )
                    }
                }
            },
            icon = {
                Icon(
                    painter = when {
                        selected && enabled -> rememberPluginIconSvgPainter(activeIconResource)
                            ?: painterResource(Res.drawable.puzzle_filled)

                        else -> rememberPluginIconSvgPainter(inactiveIconResource)
                            ?: painterResource(Res.drawable.puzzle_outlined)
                    },
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
            badge = {
                popupMenuContent?.let {
                    Box {
                        var expanded by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { expanded = true },
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = null,
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            it({ expanded = false })
                        }
                    }
                }
            },
            selected = selected,
            onClick = onClick,
            // Because NavigationDrawerItem does not have enabled parameter,
            // we manually provide better visual feedback for non-enabled plugins
            modifier = Modifier.alpha(if (enabled) 1.0f else 0.5f),
        )
        if (underAiControl) {
            // Accent rail drawn over the item so the plugin an agent is driving stands out even
            // when the list is scrolled and the label is out of view.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(2.dp),
                        ),
                )
            }
        }
    }
}
