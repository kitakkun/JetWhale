package com.kitakkun.jetwhale.host.settings.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.ui.JwMetrics
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwTheme

/**
 * One setting: its [label] (and optional [description]) on the left, the control that changes it
 * on the right. Rows share [JwMetrics.controlHeight] as a minimum so a switch and a dropdown
 * line up in the same panel.
 */
@Composable
fun SettingsItemRow(
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    controlComponent: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = JwMetrics.controlHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.extraLarge),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(JwSpacing.tiny),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = JwTheme.colors.textSecondary,
                )
            }
        }
        controlComponent()
    }
}
