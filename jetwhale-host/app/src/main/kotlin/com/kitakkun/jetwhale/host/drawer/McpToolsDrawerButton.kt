package com.kitakkun.jetwhale.host.drawer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.mcp_tools_open_all
import org.jetbrains.compose.resources.stringResource

/**
 * Drawer entry point to the MCP tools browser that is not tied to a plugin, so the browser is
 * reachable even when no plugin badge is on screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpToolsDrawerButton(onClick: () -> Unit) {
    val label = stringResource(Res.string.mcp_tools_open_all)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = label,
            )
        }
    }
}
