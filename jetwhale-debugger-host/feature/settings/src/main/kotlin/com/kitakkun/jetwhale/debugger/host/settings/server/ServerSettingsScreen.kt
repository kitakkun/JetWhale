package com.kitakkun.jetwhale.debugger.host.settings.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Start
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.debugger.host.settings.Res
import com.kitakkun.jetwhale.debugger.host.settings.SettingsScreenScaffoldPageContentPadding
import com.kitakkun.jetwhale.debugger.host.settings.component.SettingOptionView
import com.kitakkun.jetwhale.debugger.host.settings.component.SettingsItemRow
import com.kitakkun.jetwhale.debugger.host.settings.component.TextFieldSettingsItemView
import com.kitakkun.jetwhale.debugger.host.settings.server_configuration
import com.kitakkun.jetwhale.debugger.host.settings.server_status
import com.kitakkun.jetwhale.debugger.host.settings.server_status_error
import com.kitakkun.jetwhale.debugger.host.settings.server_status_running
import com.kitakkun.jetwhale.debugger.host.settings.server_status_starting
import com.kitakkun.jetwhale.debugger.host.settings.server_status_stopped
import com.kitakkun.jetwhale.debugger.host.settings.server_status_stopping
import org.jetbrains.compose.resources.stringResource

@Composable
fun ServerSettingsScreen(
    uiState: ServerSettingsScreenUiState,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = SettingsScreenScaffoldPageContentPadding,
    ) {
        item {
            SettingOptionView(stringResource(Res.string.server_status)) {
                SettingsItemRow(
                    label = when (uiState.serverState) {
                        is ServerState.Starting -> stringResource(Res.string.server_status_starting)
                        is ServerState.Running -> stringResource(Res.string.server_status_running, uiState.serverState.port)
                        is ServerState.Error -> stringResource(Res.string.server_status_error, uiState.serverState.reason)
                        is ServerState.Stopping -> stringResource(Res.string.server_status_stopping)
                        is ServerState.Stopped -> stringResource(Res.string.server_status_stopped)
                    },
                ) {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Stop, null)
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Start, null)
                    }
                }
            }
        }
        item {
            SettingOptionView(
                label = stringResource(Res.string.server_configuration),
            ) {
                TextFieldSettingsItemView(
                    label = "Server Port",
                    text = uiState.editingPort.toString(),
                    onTextChange = { },
                    readonly = !uiState.canEditPort,
                )
            }
        }
        item {
            Button(onClick = {}) {
                Text("wire ADB Transport")
            }
        }
    }
}
