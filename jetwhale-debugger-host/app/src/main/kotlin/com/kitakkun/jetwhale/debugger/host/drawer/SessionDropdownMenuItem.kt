package com.kitakkun.jetwhale.debugger.host.drawer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun SessionDropdownMenuItem(
    displayName: String,
    selected: Boolean,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenuItem(
        enabled = isActive,
        leadingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                )
            }
        },
        trailingIcon = {
            Badge(containerColor = if (isActive) Color.Green else Color.LightGray)
        },
        text = { Text(displayName) },
        onClick = onClick,
        modifier = modifier,
    )
}