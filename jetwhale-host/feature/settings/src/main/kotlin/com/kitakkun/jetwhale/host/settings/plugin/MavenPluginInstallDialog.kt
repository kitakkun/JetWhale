package com.kitakkun.jetwhale.host.settings.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.model.MavenCoordinates
import com.kitakkun.jetwhale.host.model.WellKnownMavenRepositories
import com.kitakkun.jetwhale.host.model.WellKnownMavenRepository
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
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTextField
import com.kitakkun.jetwhale.host.ui.JwTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun MavenPluginInstallDialog(
    onDismissRequest: () -> Unit,
    onInstall: (MavenCoordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    val form = remember { MavenCoordinatesFormState() }
    val fillRequiredFieldsError = stringResource(Res.string.maven_install_error_fill_required)

    JwDialog(
        onDismissRequest = onDismissRequest,
        closeLabel = stringResource(Res.string.close),
        title = stringResource(Res.string.maven_install_dialog_title),
        modifier = modifier,
        confirmButton = {
            JwButton(
                text = stringResource(Res.string.maven_install_install),
                style = JwButtonStyle.Primary,
                onClick = {
                    val coordinates = form.toCoordinates()
                    if (coordinates == null) {
                        form.errorMessage = fillRequiredFieldsError
                        return@JwButton
                    }
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
    ) {
        MavenCoordinatesForm(form = form)
    }
}

/** The fields a set of coordinates is entered through: a pasted build-script line, or one by one. */
@Composable
private fun MavenCoordinatesForm(
    form: MavenCoordinatesFormState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        JwText(
            text = stringResource(Res.string.maven_install_dialog_description),
            style = JwTheme.textStyles.body,
        )

        JwFormField(
            label = stringResource(Res.string.maven_install_paste_label),
            supportingText = stringResource(Res.string.maven_install_paste_supporting_text),
        ) {
            JwTextField(
                value = form.pastedNotation,
                onValueChange = form::onPastedNotationChange,
                placeholder = "com.example:my-plugin:1.0.0",
                textStyle = JwTheme.textStyles.code,
            )
        }

        CoordinateField(
            label = stringResource(Res.string.maven_install_group_id_label),
            value = form.groupId,
            placeholder = "com.example",
            onValueChange = form::onGroupIdChange,
        )

        CoordinateField(
            label = stringResource(Res.string.maven_install_artifact_id_label),
            value = form.artifactId,
            placeholder = "my-plugin",
            onValueChange = form::onArtifactIdChange,
        )

        CoordinateField(
            label = stringResource(Res.string.maven_install_version_label),
            value = form.version,
            placeholder = "1.0.0",
            onValueChange = form::onVersionChange,
        )

        RepositoryField(
            selected = form.selectedWellKnownRepository,
            expanded = form.repositoryMenuExpanded,
            onExpandedChange = { form.repositoryMenuExpanded = it },
            onSelectRepository = form::onWellKnownRepositorySelected,
            onSelectCustom = form::onCustomRepositorySelected,
        )

        if (form.selectedWellKnownRepository == null) {
            CoordinateField(
                label = stringResource(Res.string.maven_install_repository_url_label),
                value = form.repositoryUrl,
                placeholder = MavenCoordinates.MAVEN_CENTRAL_URL,
                onValueChange = form::onRepositoryUrlChange,
            )
        }

        form.errorMessage?.let { error ->
            JwText(
                text = error,
                color = JwTheme.colors.error,
                style = JwTheme.textStyles.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** One labelled monospaced text field of the coordinates form. */
@Composable
private fun CoordinateField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    JwFormField(label = label, modifier = modifier) {
        JwTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            textStyle = JwTheme.textStyles.code,
        )
    }
}

/** The repository picker: the well-known repositories plus "custom", which reveals a URL field. */
@Composable
private fun RepositoryField(
    selected: WellKnownMavenRepository?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectRepository: (WellKnownMavenRepository) -> Unit,
    onSelectCustom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JwFormField(
        label = stringResource(Res.string.maven_install_repository_label),
        supportingText = selected?.url,
        modifier = modifier,
    ) {
        JwDropdownButton(
            text = selected?.displayName ?: stringResource(Res.string.maven_install_repository_custom),
            expanded = expanded,
            onExpandedChange = onExpandedChange,
        ) {
            WellKnownMavenRepositories.entries.forEach { repository ->
                JwMenuItem(
                    text = repository.displayName,
                    selected = repository == selected,
                    trailingIcon = { RepositoryUrlHint(url = repository.url) },
                    onClick = { onSelectRepository(repository) },
                )
            }
            JwMenuItem(
                text = stringResource(Res.string.maven_install_repository_custom),
                selected = selected == null,
                onClick = onSelectCustom,
            )
        }
    }
}

@Composable
private fun RepositoryUrlHint(
    url: String,
    modifier: Modifier = Modifier,
) {
    JwText(
        text = url,
        style = JwTheme.textStyles.bodySmall,
        color = JwTheme.colors.textSecondary,
        modifier = modifier,
    )
}

/**
 * What the user has typed into the Maven install dialog.
 *
 * Pasting a build-script line fills the individual fields, so the two ways of entering coordinates
 * have to share one piece of state rather than each owning their own.
 */
private class MavenCoordinatesFormState {
    var pastedNotation: String by mutableStateOf("")
        private set
    var groupId: String by mutableStateOf("")
        private set
    var artifactId: String by mutableStateOf("")
        private set
    var version: String by mutableStateOf("")
        private set
    var repositoryUrl: String by mutableStateOf(MavenCoordinates.MAVEN_CENTRAL_URL)
        private set
    var repositoryMenuExpanded: Boolean by mutableStateOf(false)
    var errorMessage: String? by mutableStateOf(null)

    private var useCustomRepository: Boolean by mutableStateOf(false)

    val selectedWellKnownRepository: WellKnownMavenRepository?
        get() = WellKnownMavenRepositories.matching(repositoryUrl).takeUnless { useCustomRepository }

    fun onPastedNotationChange(input: String) {
        pastedNotation = input
        MavenCoordinates.parseLenient(input)?.let { parsed ->
            groupId = parsed.groupId
            artifactId = parsed.artifactId
            version = parsed.version
            repositoryUrl = parsed.repositoryUrl
            useCustomRepository = WellKnownMavenRepositories.matching(parsed.repositoryUrl) == null
        }
        errorMessage = null
    }

    fun onGroupIdChange(input: String) {
        groupId = input
        errorMessage = null
    }

    fun onArtifactIdChange(input: String) {
        artifactId = input
        errorMessage = null
    }

    fun onVersionChange(input: String) {
        version = input
        errorMessage = null
    }

    fun onRepositoryUrlChange(input: String) {
        repositoryUrl = input
        errorMessage = null
    }

    fun onWellKnownRepositorySelected(repository: WellKnownMavenRepository) {
        repositoryUrl = repository.url
        useCustomRepository = false
        repositoryMenuExpanded = false
        errorMessage = null
    }

    fun onCustomRepositorySelected() {
        useCustomRepository = true
        repositoryMenuExpanded = false
        errorMessage = null
    }

    /** The coordinates the form describes, or null while a required field is still blank. */
    fun toCoordinates(): MavenCoordinates? {
        if (groupId.isBlank() || artifactId.isBlank() || version.isBlank()) return null
        return MavenCoordinates(
            groupId = groupId.trim(),
            artifactId = artifactId.trim(),
            version = version.trim(),
            repositoryUrl = repositoryUrl.trim().ifBlank { MavenCoordinates.MAVEN_CENTRAL_URL },
        )
    }
}

@Preview
@Composable
private fun MavenPluginInstallDialogPreview() {
    JwTheme(darkTheme = false) {
        MavenPluginInstallDialog(
            onDismissRequest = {},
            onInstall = {},
        )
    }
}
