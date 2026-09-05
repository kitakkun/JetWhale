package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A labeled slot in a form: the [label] above the control, an optional [supportingText] under it.
 * Labels sit above rather than inside the field, so a filled field keeps its name and rows stay
 * [JwMetrics.controlHeight] tall.
 *
 * @param label the field's name.
 * @param supportingText a hint under the control, or the validation message while [isError].
 * @param isError colors [supportingText] as an error.
 * @param content the control: a [JwTextField], a [JwDropdownButton], a row of [JwTag]s.
 */
@Composable
public fun JwFormField(
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    isError: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = JwTheme.colors.textSecondary,
        )
        content()
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else JwTheme.colors.textDisabled,
            )
        }
    }
}
