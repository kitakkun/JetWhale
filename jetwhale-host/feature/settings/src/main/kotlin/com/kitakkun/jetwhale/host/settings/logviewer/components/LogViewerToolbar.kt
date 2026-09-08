package com.kitakkun.jetwhale.host.settings.logviewer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.settings.Res
import com.kitakkun.jetwhale.host.settings.log_viewer_auto_scroll
import com.kitakkun.jetwhale.host.settings.log_viewer_clear_filter
import com.kitakkun.jetwhale.host.settings.log_viewer_clear_logs
import com.kitakkun.jetwhale.host.settings.log_viewer_filter_placeholder
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwCheckbox
import com.kitakkun.jetwhale.host.ui.JwHorizontalDivider
import com.kitakkun.jetwhale.host.ui.JwSearchField
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun LogViewerToolbar(
    filterText: String,
    autoScroll: Boolean,
    onFilterTextChange: (String) -> Unit,
    onAutoScrollChange: (Boolean) -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A flat bar with a hairline under it, like every other toolbar in the host.
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(JwTheme.colors.toolbarBackground)
                .padding(JwSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterTextField(
                value = filterText,
                onValueChange = onFilterTextChange,
                modifier = Modifier.weight(1f),
            )

            AutoScrollCheckbox(
                checked = autoScroll,
                onCheckedChange = onAutoScrollChange,
            )

            JwButton(
                text = stringResource(Res.string.log_viewer_clear_logs),
                onClick = onClearLogs,
                style = JwButtonStyle.Primary,
            )
        }
        JwHorizontalDivider()
    }
}

@Composable
private fun FilterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    JwSearchField(
        value = value,
        onValueChange = onValueChange,
        clearLabel = stringResource(Res.string.log_viewer_clear_filter),
        placeholder = stringResource(Res.string.log_viewer_filter_placeholder),
        modifier = modifier,
    )
}

@Composable
private fun AutoScrollCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    JwCheckbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        label = stringResource(Res.string.log_viewer_auto_scroll),
    )
}
