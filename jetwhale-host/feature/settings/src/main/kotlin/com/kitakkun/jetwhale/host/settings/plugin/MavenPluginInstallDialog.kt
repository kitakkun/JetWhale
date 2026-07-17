package com.kitakkun.jetwhale.host.settings.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.model.MavenCoordinates

@Composable
fun MavenPluginInstallDialog(
    onDismissRequest: () -> Unit,
    onInstall: (MavenCoordinates) -> Unit,
) {
    var groupId by remember { mutableStateOf("") }
    var artifactId by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("") }
    var repositoryUrl by remember { mutableStateOf(MavenCoordinates.MAVEN_CENTRAL_URL) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text("Install Plugin from Maven Repository")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Enter Maven coordinates to download and install a plugin.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = groupId,
                    onValueChange = {
                        groupId = it
                        errorMessage = null
                    },
                    label = { Text("Group ID") },
                    placeholder = { Text("com.example") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = artifactId,
                    onValueChange = {
                        artifactId = it
                        errorMessage = null
                    },
                    label = { Text("Artifact ID") },
                    placeholder = { Text("my-plugin") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = version,
                    onValueChange = {
                        version = it
                        errorMessage = null
                    },
                    label = { Text("Version") },
                    placeholder = { Text("1.0.0") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = repositoryUrl,
                    onValueChange = {
                        repositoryUrl = it
                        errorMessage = null
                    },
                    label = { Text("Repository URL") },
                    placeholder = { Text(MavenCoordinates.MAVEN_CENTRAL_URL) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (groupId.isBlank() || artifactId.isBlank() || version.isBlank()) {
                            errorMessage = "Please fill in all required fields."
                            return@Button
                        }
                        val coordinates = MavenCoordinates(
                            groupId = groupId.trim(),
                            artifactId = artifactId.trim(),
                            version = version.trim(),
                            repositoryUrl = repositoryUrl.trim().ifBlank { MavenCoordinates.MAVEN_CENTRAL_URL },
                        )
                        onInstall(coordinates)
                        onDismissRequest()
                    },
                ) {
                    Text("Install")
                }
            }
        },
    )
}
