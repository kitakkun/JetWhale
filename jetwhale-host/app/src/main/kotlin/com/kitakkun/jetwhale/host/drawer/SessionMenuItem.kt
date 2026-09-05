package com.kitakkun.jetwhale.host.drawer

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.SessionTransportSecurity
import com.kitakkun.jetwhale.host.session_local_connection
import com.kitakkun.jetwhale.host.session_secure_connection
import com.kitakkun.jetwhale.host.ui.JwMenuItem
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import org.jetbrains.compose.resources.stringResource

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
            tint = JwTone.Success.color,
            modifier = modifier.size(12.dp),
        )

        SessionTransportSecurity.LOOPBACK -> Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = stringResource(Res.string.session_local_connection),
            tint = JwTheme.colors.textSecondary,
            modifier = modifier.size(12.dp),
        )

        SessionTransportSecurity.PLAINTEXT -> Unit
    }
}

/** One session in a picker menu: its app icon, [displayName], and the transport lock. */
@Composable
fun SessionMenuItem(
    session: DebugSession,
    displayName: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JwMenuItem(
        text = displayName,
        selected = selected,
        enabled = session.isActive,
        leading = { AppIcon(session) },
        trailing = { SessionSecurityIcon(session.transportSecurity) },
        onClick = onClick,
        modifier = modifier,
    )
}
