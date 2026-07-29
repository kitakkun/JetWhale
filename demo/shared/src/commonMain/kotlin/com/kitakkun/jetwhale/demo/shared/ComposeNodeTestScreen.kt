package com.kitakkun.jetwhale.demo.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A screen with one of each kind of semantics node, so the Compose Semantics Inspector plugin has
 * something worth looking at: labelled and unlabelled controls, an editable field, toggleable and
 * selectable state, a disabled node, a scrollable list, and a dialog — which composes into a
 * **separate** Compose root and therefore shows up as a second root in the plugin.
 */
@Composable
fun ComposeNodeTestScreen() {
    var clicks by remember { mutableStateOf(0) }
    var input by remember { mutableStateOf("") }
    var checked by remember { mutableStateOf(false) }
    var switched by remember { mutableStateOf(true) }
    var selectedOption by remember { mutableStateOf("Alpha") }
    var dialogVisible by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Capture this screen from the Compose Semantics Inspector plugin.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            Button(
                onClick = { clicks++ },
                modifier = Modifier.testTag("increment-button"),
            ) {
                Text("Clicked $clicks time(s)")
            }
        }
        item {
            Button(onClick = {}, enabled = false) {
                Text("Disabled button")
            }
        }
        item {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Editable field") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("demo-text-field"),
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checked, onCheckedChange = { checked = it }, modifier = Modifier.testTag("demo-checkbox"))
                Text("Checkbox")
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = switched, onCheckedChange = { switched = it })
                Text("Switch")
            }
        }
        item {
            Column {
                listOf("Alpha", "Beta", "Gamma").forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.selectable(
                            selected = selectedOption == option,
                            onClick = { selectedOption = option },
                        ),
                    ) {
                        RadioButton(selected = selectedOption == option, onClick = { selectedOption = option })
                        Text(option)
                    }
                }
            }
        }
        item {
            // A node whose only label is a contentDescription: it has nothing to read on screen,
            // but an agent can still find and describe it.
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics { contentDescription = "Decorative banner" },
            ) {}
        }
        item {
            Button(onClick = { dialogVisible = true }, modifier = Modifier.testTag("open-dialog-button")) {
                Text("Open dialog (a second Compose root)")
            }
        }
        items(SCROLLABLE_ROW_COUNT) { index ->
            Text("List row #$index", style = MaterialTheme.typography.bodySmall)
        }
    }

    if (dialogVisible) {
        AlertDialog(
            onDismissRequest = { dialogVisible = false },
            title = { Text("Dialog") },
            text = { Text("This dialog is its own Compose root — the plugin lists it separately.") },
            confirmButton = {
                TextButton(onClick = { dialogVisible = false }, modifier = Modifier.testTag("dialog-close-button")) {
                    Text("Close")
                }
            },
        )
    }
}

private const val SCROLLABLE_ROW_COUNT = 30
