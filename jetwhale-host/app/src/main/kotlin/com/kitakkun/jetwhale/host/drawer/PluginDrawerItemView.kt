package com.kitakkun.jetwhale.host.drawer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.model.PluginIconResource
import com.kitakkun.jetwhale.host.plugin_actions
import com.kitakkun.jetwhale.host.puzzle_filled
import com.kitakkun.jetwhale.host.puzzle_outlined
import com.kitakkun.jetwhale.host.ui.JwDropdownMenu
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwIconButton
import com.kitakkun.jetwhale.host.ui.JwIconButtonDefaults
import com.kitakkun.jetwhale.host.ui.JwIcons
import com.kitakkun.jetwhale.host.ui.JwListItem
import com.kitakkun.jetwhale.host.ui.JwMetrics
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwTagStyle
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Matches the small shape the row is clipped to. */
private val AiRingCornerRadius = 4.dp

/**
 * How far an inactive row is faded. The list's own muted color sits too close to a normal row's in the
 * dark theme to tell them apart, and the row must stay clickable, which rules out disabling it.
 */
private const val INACTIVE_ROW_ALPHA = 0.45f

/** Shrinks the "opens elsewhere" glyph to the tag's height. */
private val BadgeIconInset = 3.dp

/**
 * One plugin in the drawer. An inactive plugin — switched off, or not usable right now — is drawn
 * greyed out but stays clickable, so clicking it can explain why.
 */
@Composable
fun PluginDrawerItemView(
    active: Boolean,
    name: String,
    selected: Boolean,
    underAiControl: Boolean,
    exposesMcpTools: Boolean,
    activeIconResource: PluginIconResource?,
    inactiveIconResource: PluginIconResource?,
    onClick: () -> Unit,
    onClickMcpBadge: () -> Unit,
    modifier: Modifier = Modifier,
    popupMenuContent: (@Composable ColumnScope.(dismiss: () -> Unit) -> Unit)? = null,
) {
    Box(modifier = modifier) {
        JwListItem(
            text = name,
            selected = selected,
            muted = !active,
            modifier = if (active) Modifier else Modifier.alpha(INACTIVE_ROW_ALPHA),
            onClick = onClick,
            leadingContent = {
                JwIcon(
                    painter = when {
                        selected && active -> rememberPluginIconSvgPainter(activeIconResource)
                            ?: painterResource(Res.drawable.puzzle_filled)

                        else -> rememberPluginIconSvgPainter(inactiveIconResource)
                            ?: painterResource(Res.drawable.puzzle_outlined)
                    },
                    contentDescription = null,
                )
            },
            trailingContent = {
                if (exposesMcpTools) {
                    McpBadge(
                        operating = underAiControl,
                        onClick = onClickMcpBadge,
                    )
                }
                popupMenuContent?.let { menuContent ->
                    Box {
                        var expanded by remember { mutableStateOf(false) }
                        JwIconButton(
                            onClick = { expanded = true },
                            tooltip = stringResource(Res.string.plugin_actions),
                            size = JwIconButtonDefaults.inlineSize,
                        ) {
                            JwIcon(imageVector = JwIcons.MoreHorizontal, contentDescription = null)
                        }
                        JwDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            menuContent { expanded = false }
                        }
                    }
                }
            },
        )
        if (underAiControl) {
            // A rotating gradient ring drawn over the row makes the plugin an agent is driving
            // unmistakable even when the list is scrolled and the label is out of view.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .aiOperatingBorder(color = JwTheme.colors.aiAccent, width = JwMetrics.focusStrokeWidth, cornerRadius = AiRingCornerRadius),
            )
        }
    }
}

/**
 * A compact "MCP" tag shown after a plugin's name when it exposes MCP tools. It fills with the AI
 * accent while an agent is running one of those tools and stays outlined otherwise, and opens the
 * MCP tools browser scoped to this plugin.
 */
@Composable
private fun McpBadge(
    operating: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JwTag(
        text = "MCP",
        modifier = modifier,
        tone = if (operating) JwTone.Warning else JwTone.Neutral,
        style = if (operating) JwTagStyle.Filled else JwTagStyle.Outlined,
        onClick = onClick,
        trailingIcon = {
            // Signals that clicking opens a separate window.
            JwIcon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.padding(BadgeIconInset),
            )
        },
    )
}

@Preview
@Composable
private fun PluginDrawerItemViewPreview() {
    JwTheme(darkTheme = false) {
        PluginDrawerItemView(
            active = true,
            name = "Inspector",
            selected = true,
            underAiControl = true,
            exposesMcpTools = true,
            activeIconResource = null,
            inactiveIconResource = null,
            onClick = {},
            onClickMcpBadge = {},
        )
    }
}
