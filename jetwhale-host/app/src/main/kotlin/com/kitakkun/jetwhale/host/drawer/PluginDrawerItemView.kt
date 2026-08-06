package com.kitakkun.jetwhale.host.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.model.PluginIconResource
import com.kitakkun.jetwhale.host.plugin_headless_badge
import com.kitakkun.jetwhale.host.plugin_headless_badge_tooltip
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
    isHeadless: Boolean,
    activeIconResource: PluginIconResource?,
    inactiveIconResource: PluginIconResource?,
    onClick: () -> Unit,
    onClickMcpBadge: () -> Unit,
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
                    // fill = false lets a long name shrink and ellipsize instead of pushing the
                    // badge off the row.
                    Text(
                        text = name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (exposesMcpTools) {
                        McpBadge(
                            operating = underAiControl,
                            onClick = onClickMcpBadge,
                        )
                    }
                    if (isHeadless) {
                        NoUiBadge()
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
            // A rotating gradient ring drawn over the item makes the plugin an agent is driving
            // unmistakable even when the list is scrolled and the label is out of view.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .aiOperatingBorder(color = AiOperatingAccentColor, width = 2.dp),
            )
        }
    }
}

/**
 * A compact "MCP" badge shown after a plugin's name when it exposes MCP tools. It is filled while an
 * agent is running one of those tools and outlined otherwise, and opens the MCP tools browser scoped
 * to this plugin.
 */
@Composable
private fun McpBadge(
    operating: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(4.dp)
    val contentColor = if (operating) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
    val decoration = if (operating) {
        Modifier.background(AiOperatingAccentColor, shape)
    } else {
        Modifier.border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, shape)
    }
    Row(
        modifier = Modifier
            .clip(shape)
            .then(decoration)
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = "MCP",
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
        // Signals that clicking opens a separate window.
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(11.dp),
        )
    }
}

/**
 * A compact "No UI" badge shown after a plugin's name when the plugin renders nothing. Without it a
 * headless plugin is indistinguishable in the list from one whose UI failed to draw.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoUiBadge() {
    val shape = RoundedCornerShape(4.dp)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip {
                Text(stringResource(Res.string.plugin_headless_badge_tooltip))
            }
        },
        state = rememberTooltipState(),
    ) {
        Text(
            text = stringResource(Res.string.plugin_headless_badge),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, shape)
                .padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}
