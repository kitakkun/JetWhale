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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.model.SessionTransportSecurity
import com.kitakkun.jetwhale.host.session_local_connection
import com.kitakkun.jetwhale.host.session_secure_connection
import org.jetbrains.compose.resources.stringResource

/** Green used for the secure-connection (wss) lock; readable on both light and dark surfaces. */
val SecureSessionGreen: Color = Color(0xFF43A047)

/**
 * Shows a lock indicator for the session transport: a green lock for TLS (wss), a neutral lock for
 * a loopback (ADB-forwarded) connection which is effectively secure, and nothing for plaintext.
 */
@Composable
fun SessionSecurityIcon(
    transportSecurity: SessionTransportSecurity,
    modifier: Modifier = Modifier,
) {
    when (transportSecurity) {
        SessionTransportSecurity.TLS -> Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = stringResource(Res.string.session_secure_connection),
            tint = SecureSessionGreen,
            modifier = modifier.size(14.dp),
        )

        SessionTransportSecurity.LOOPBACK -> Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = stringResource(Res.string.session_local_connection),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.size(14.dp),
        )

        SessionTransportSecurity.PLAINTEXT -> Unit
    }
}

@Composable
fun SessionDropdownMenuItem(
    displayName: String,
    selected: Boolean,
    isActive: Boolean,
    transportSecurity: SessionTransportSecurity,
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
                SessionSecurityIcon(transportSecurity)
                Badge(containerColor = if (isActive) Color.Green else Color.LightGray)
            }
        },
        text = { Text(displayName) },
        onClick = onClick,
        modifier = modifier,
    )
}
