package com.kitakkun.jetwhale.plugins.example.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.sdk.rememberPersistent
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwFormField
import com.kitakkun.jetwhale.host.ui.JwHorizontalDivider
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTextField
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwToolbar

/**
 * The plugin's entry point: everything that only a running host can supply — the persisted draft
 * input — is owned here, so [ExamplePluginView] itself is a pure function of its arguments.
 */
@Composable
fun ExamplePluginViewRoot(
    eventLogs: List<String>,
    onClickSendPing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Demonstrates rememberPersistent: this text is saved to the plugin's own pluginId-scoped
    // storage and survives plugin reloads and host restarts.
    var persistedInput by rememberPersistent("draft-input", default = "")

    ExamplePluginView(
        eventLogs = eventLogs,
        persistedInput = persistedInput,
        onClickSendPing = onClickSendPing,
        onClickTriggerUIError = { error("Example Error") },
        onPersistedInputChange = { persistedInput = it },
        modifier = modifier,
    )
}
