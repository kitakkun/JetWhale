package com.kitakkun.jetwhale.host.drawer

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.SessionTransportSecurity
import com.kitakkun.jetwhale.host.session_local_connection
import com.kitakkun.jetwhale.host.session_secure_connection
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwMenuItem
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/** Smaller than a regular glyph: the lock is a secondary annotation beside the session name. */
private val SecurityIconSize = 12.dp

/**
 * Shows a lock indicator for the session transport: a green lock for TLS (wss), a neutral lock for
 * a loopback (ADB-forwarded) connection which is effectively secure, and nothing for plaintext.
 */
@Composable
fun SessionSecurityIcon(
    transportSecurity: SessionTransportSecurity,
    modifier: Modifier = Modifier,
) {
    val description = when (transportSecurity) {
        SessionTransportSecurity.TLS -> stringResource(Res.string.session_secure_connection)
        SessionTransportSecurity.LOOPBACK -> stringResource(Res.string.session_local_connection)
        SessionTransportSecurity.PLAINTEXT -> return
    }
    JwIcon(
        imageVector = Icons.Default.Lock,
        contentDescription = description,
        tint = if (transportSecurity == SessionTransportSecurity.TLS) {
            JwTone.Success.color
        } else {
            JwTheme.colors.textSecondary
        },
        modifier = modifier.size(SecurityIconSize),
    )
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
        leadingIcon = { AppIcon(session) },
        trailingIcon = { SessionSecurityIcon(session.transportSecurity) },
        onClick = onClick,
        modifier = modifier,
    )
}

@Preview
@Composable
private fun SessionSecurityIconPreview() {
    JwTheme(darkTheme = false) {
        SessionSecurityIcon(transportSecurity = SessionTransportSecurity.TLS)
    }
}

@Preview
@Composable
private fun SessionMenuItemPreview() {
    JwTheme(darkTheme = false) {
        SessionMenuItem(
            session = DebugSession(
                id = "session-1",
                name = "Sample app",
                isActive = true,
                transportSecurity = SessionTransportSecurity.LOOPBACK,
                installedPlugins = persistentListOf(),
                appName = "Sample app",
                deviceId = "device-1",
                deviceName = "Pixel 9",
            ),
            displayName = "Sample app",
            selected = true,
            onClick = {},
        )
    }
}
