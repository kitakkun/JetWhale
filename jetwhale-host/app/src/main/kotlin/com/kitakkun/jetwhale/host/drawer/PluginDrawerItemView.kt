package com.kitakkun.jetwhale.host.drawer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.model.PluginIconResource
import com.kitakkun.jetwhale.host.puzzle_filled
import com.kitakkun.jetwhale.host.puzzle_outlined
import org.jetbrains.compose.resources.painterResource

@Composable
fun PluginDrawerItemView(
    enabled: Boolean,
    name: String,
    selected: Boolean,
    activeIconResource: PluginIconResource?,
    inactiveIconResource: PluginIconResource?,
    onClick: () -> Unit,
    popupMenuContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Box {
        NavigationDrawerItem(
            label = { Text(name) },
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
                            onClick = { expanded = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = null,
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            it()
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
    }
}
