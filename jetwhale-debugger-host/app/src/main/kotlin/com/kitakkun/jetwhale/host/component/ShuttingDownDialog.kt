package com.kitakkun.jetwhale.host.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.shutting_down
import org.jetbrains.compose.resources.stringResource

@Composable
fun ShuttingDownDialog() {
    Dialog(onDismissRequest = {}) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.shutting_down),
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
            CircularWavyProgressIndicator()
        }
    }
}
