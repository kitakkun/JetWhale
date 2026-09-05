package com.kitakkun.jetwhale.host.settings.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import com.kitakkun.jetwhale.host.settings.close
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
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwDialog
import com.kitakkun.jetwhale.host.ui.JwDropdownButton
import com.kitakkun.jetwhale.host.ui.JwFormField
import com.kitakkun.jetwhale.host.ui.JwMenuItem
import com.kitakkun.jetwhale.host.ui.JwTextField
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTypography
import org.jetbrains.compose.resources.stringResource

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

    JwDialog(
        onDismissRequest = onDismissRequest,
        closeLabel = stringResource(Res.string.close),
        title = stringResource(Res.string.maven_install_dialog_title),
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.maven_install_dialog_description),
                    style = MaterialTheme.typography.bodyMedium,
                )

                JwFormField(
                    label = stringResource(Res.string.maven_install_paste_label),
                    supportingText = stringResource(Res.string.maven_install_paste_supporting_text),
                ) {
                    JwTextField(
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
                        placeholder = "com.example:my-plugin:1.0.0",
                        textStyle = JwTypography.code,
                    )
                }

                JwFormField(label = stringResource(Res.string.maven_install_group_id_label)) {
                    JwTextField(
                        value = groupId,
                        onValueChange = {
                            groupId = it
                            errorMessage = null
                        },
                        placeholder = "com.example",
                        textStyle = JwTypography.code,
                    )
                }

                JwFormField(label = stringResource(Res.string.maven_install_artifact_id_label)) {
                    JwTextField(
                        value = artifactId,
                        onValueChange = {
                            artifactId = it
                            errorMessage = null
                        },
                        placeholder = "my-plugin",
                        textStyle = JwTypography.code,
                    )
                }

                JwFormField(label = stringResource(Res.string.maven_install_version_label)) {
                    JwTextField(
                        value = version,
                        onValueChange = {
                            version = it
                            errorMessage = null
                        },
                        placeholder = "1.0.0",
                        textStyle = JwTypography.code,
                    )
                }

                JwFormField(
                    label = stringResource(Res.string.maven_install_repository_label),
                    supportingText = selectedWellKnownRepository?.url,
                ) {
                    JwDropdownButton(
                        text = selectedWellKnownRepository?.displayName
                            ?: stringResource(Res.string.maven_install_repository_custom),
                        expanded = repositoryMenuExpanded,
                        onExpandedChange = { repositoryMenuExpanded = it },
                    ) {
                        WellKnownMavenRepositories.entries.forEach { repository ->
                            JwMenuItem(
                                text = repository.displayName,
                                selected = repository == selectedWellKnownRepository,
                                trailing = {
                                    Text(
                                        text = repository.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = JwTheme.colors.textSecondary,
                                    )
                                },
                                onClick = {
                                    repositoryUrl = repository.url
                                    useCustomRepository = false
                                    repositoryMenuExpanded = false
                                    errorMessage = null
                                },
                            )
                        }
                        JwMenuItem(
                            text = stringResource(Res.string.maven_install_repository_custom),
                            selected = selectedWellKnownRepository == null,
                            onClick = {
                                useCustomRepository = true
                                repositoryMenuExpanded = false
                                errorMessage = null
                            },
                        )
                    }
                }

                if (selectedWellKnownRepository == null) {
                    JwFormField(label = stringResource(Res.string.maven_install_repository_url_label)) {
                        JwTextField(
                            value = repositoryUrl,
                            onValueChange = {
                                repositoryUrl = it
                                errorMessage = null
                            },
                            placeholder = MavenCoordinates.MAVEN_CENTRAL_URL,
                            textStyle = JwTypography.code,
                        )
                    }
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
            JwButton(
                text = stringResource(Res.string.maven_install_install),
                style = JwButtonStyle.Primary,
                onClick = {
                    if (groupId.isBlank() || artifactId.isBlank() || version.isBlank()) {
                        errorMessage = fillRequiredFieldsError
                        return@JwButton
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
            )
        },
        dismissButton = {
            JwButton(
                text = stringResource(Res.string.dialog_cancel),
                onClick = onDismissRequest,
                style = JwButtonStyle.Text,
            )
        },
    )
}
