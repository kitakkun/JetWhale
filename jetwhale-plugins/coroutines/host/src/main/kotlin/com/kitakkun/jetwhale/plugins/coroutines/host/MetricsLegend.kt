package com.kitakkun.jetwhale.plugins.coroutines.host

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme

/** What a table's columns measure, under the table: the headers are too short to say it. */
@Composable
internal fun MetricsLegend(text: String, modifier: Modifier = Modifier) {
    JwText(
        text = text,
        style = JwTheme.textStyles.labelSmall,
        color = JwTheme.colors.textSecondary,
        modifier = modifier.fillMaxWidth().padding(horizontal = JwSpacing.large, vertical = JwSpacing.small),
    )
}
