package com.kitakkun.jetwhale.host.drawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.session_secure_connection
import org.jetbrains.compose.resources.stringResource

/** Green used for the secure-connection (wss) lock; readable on both light and dark surfaces. */
val SecureSessionGreen: Color = Color(0xFF43A047)

@Composable
fun SessionDropdownMenuItem(
    displayName: String,
    selected: Boolean,
    isActive: Boolean,
    isSecure: Boolean,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isSecure) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(Res.string.session_secure_connection),
                        tint = SecureSessionGreen,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Badge(containerColor = if (isActive) Color.Green else Color.LightGray)
            }
        },
        text = { Text(displayName) },
        onClick = onClick,
        modifier = modifier,
    )
}
