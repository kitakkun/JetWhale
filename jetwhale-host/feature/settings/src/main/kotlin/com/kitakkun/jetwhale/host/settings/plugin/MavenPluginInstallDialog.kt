package com.kitakkun.jetwhale.host.settings.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import com.kitakkun.jetwhale.host.model.WellKnownMavenRepositories
import com.kitakkun.jetwhale.host.settings.Res
import com.kitakkun.jetwhale.host.settings.dialog_cancel
import com.kitakkun.jetwhale.host.settings.maven_install_artifact_id_label
import com.kitakkun.jetwhale.host.settings.maven_install_dialog_description
import com.kitakkun.jetwhale.host.settings.maven_install_dialog_title
import com.kitakkun.jetwhale.host.settings.maven_install_error_fill_required
import com.kitakkun.jetwhale.host.settings.maven_install_group_id_label
import com.kitakkun.jetwhale.host.settings.maven_install_install
import com.kitakkun.jetwhale.host.settings.maven_install_paste_label
import com.kitakkun.jetwhale.host.settings.maven_install_paste_supporting_text
import com.kitakkun.jetwhale.host.settings.maven_install_repository_custom
import com.kitakkun.jetwhale.host.settings.maven_install_repository_label
import com.kitakkun.jetwhale.host.settings.maven_install_repository_url_label
import com.kitakkun.jetwhale.host.settings.maven_install_version_label
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MavenPluginInstallDialog(
    onDismissRequest: () -> Unit,
    onInstall: (MavenCoordinates) -> Unit,
) {
    var pastedNotation by remember { mutableStateOf("") }
    var groupId by remember { mutableStateOf("") }
    var artifactId by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("") }
    var repositoryUrl by remember { mutableStateOf(MavenCoordinates.MAVEN_CENTRAL_URL) }
    var useCustomRepository by remember { mutableStateOf(false) }
    var repositoryMenuExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val selectedWellKnownRepository = WellKnownMavenRepositories.matching(repositoryUrl)
        .takeUnless { useCustomRepository }
    val fillRequiredFieldsError = stringResource(Res.string.maven_install_error_fill_required)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(stringResource(Res.string.maven_install_dialog_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.maven_install_dialog_description),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = pastedNotation,
                    onValueChange = { input ->
                        pastedNotation = input
                        MavenCoordinates.parseLenient(input)?.let { parsed ->
                            groupId = parsed.groupId
                            artifactId = parsed.artifactId
                            version = parsed.version
                            repositoryUrl = parsed.repositoryUrl
                            useCustomRepository = WellKnownMavenRepositories.matching(parsed.repositoryUrl) == null
                        }
                        errorMessage = null
                    },
                    label = { Text(stringResource(Res.string.maven_install_paste_label)) },
                    placeholder = { Text("com.example:my-plugin:1.0.0") },
                    supportingText = {
                        Text(stringResource(Res.string.maven_install_paste_supporting_text))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = groupId,
                    onValueChange = {
                        groupId = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(Res.string.maven_install_group_id_label)) },
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
                    label = { Text(stringResource(Res.string.maven_install_artifact_id_label)) },
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
                    label = { Text(stringResource(Res.string.maven_install_version_label)) },
                    placeholder = { Text("1.0.0") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                ExposedDropdownMenuBox(
                    expanded = repositoryMenuExpanded,
                    onExpandedChange = { repositoryMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedWellKnownRepository?.displayName
                            ?: stringResource(Res.string.maven_install_repository_custom),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(Res.string.maven_install_repository_label)) },
                        supportingText = selectedWellKnownRepository?.let { repository ->
                            { Text(repository.url) }
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = repositoryMenuExpanded) },
                        singleLine = true,
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = repositoryMenuExpanded,
                        onDismissRequest = { repositoryMenuExpanded = false },
                    ) {
                        WellKnownMavenRepositories.entries.forEach { repository ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(repository.displayName)
                                        Text(
                                            text = repository.url,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = {
                                    repositoryUrl = repository.url
                                    useCustomRepository = false
                                    repositoryMenuExpanded = false
                                    errorMessage = null
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.maven_install_repository_custom)) },
                            onClick = {
                                useCustomRepository = true
                                repositoryMenuExpanded = false
                                errorMessage = null
                            },
                        )
                    }
                }

                if (selectedWellKnownRepository == null) {
                    OutlinedTextField(
                        value = repositoryUrl,
                        onValueChange = {
                            repositoryUrl = it
                            errorMessage = null
                        },
                        label = { Text(stringResource(Res.string.maven_install_repository_url_label)) },
                        placeholder = { Text(MavenCoordinates.MAVEN_CENTRAL_URL) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

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
            Button(
                onClick = {
                    if (groupId.isBlank() || artifactId.isBlank() || version.isBlank()) {
                        errorMessage = fillRequiredFieldsError
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
                Text(stringResource(Res.string.maven_install_install))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.dialog_cancel))
            }
        },
    )
}
