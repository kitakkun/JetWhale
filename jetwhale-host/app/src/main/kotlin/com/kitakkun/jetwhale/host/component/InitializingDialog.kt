package com.kitakkun.jetwhale.host.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.initializing
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.unlocking_trust_registry
import org.jetbrains.compose.resources.stringResource

@Composable
fun InitializingDialog(verifyingTrustRegistry: Boolean) {
    Dialog(onDismissRequest = {}) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(JwSpacing.extraLarge, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            JwText(
                text = stringResource(Res.string.initializing),
                color = JwTheme.colors.onTooltip,
            )
            CircularWavyProgressIndicator()
            // Shown only while the signed trust registry is being verified against the OS credential
            // store, so the (blocking) Keychain prompt appears with in-app context explaining it.
            if (verifyingTrustRegistry) {
                JwText(
                    text = stringResource(Res.string.unlocking_trust_registry),
                    color = JwTheme.colors.onTooltip,
                    style = JwTheme.textStyles.bodySmall,
                )
            }
        }
    }
}
