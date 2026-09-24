package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview
@Composable
private fun JwTablePreview() {
    JwTheme(darkTheme = false) {
        JwTable(
            items = listOf("GET /api/users", "POST /api/orders"),
            columns = listOf(
                JwTableColumn(header = "Request", width = JwColumnWidth.Weight(1f)) { item ->
                    JwTableCellText(text = item)
                },
            ),
            modifier = Modifier.height(96.dp),
        )
    }
}

@Preview
@Composable
private fun JwTableCellTextPreview() {
    JwTheme(darkTheme = false) {
        JwTableCellText(text = "https://example.com/api/users", style = JwTheme.textStyles.code)
    }
}
