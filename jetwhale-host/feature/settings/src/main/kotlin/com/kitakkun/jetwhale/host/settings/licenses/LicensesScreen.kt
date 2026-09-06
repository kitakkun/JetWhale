package com.kitakkun.jetwhale.host.settings.licenses

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.settings.Res
import com.kitakkun.jetwhale.host.settings.close
import com.kitakkun.jetwhale.host.settings.licenses_back
import com.kitakkun.jetwhale.host.settings.licenses_no_license_text
import com.kitakkun.jetwhale.host.settings.licenses_open_website
import com.kitakkun.jetwhale.host.settings.licenses_title
import com.kitakkun.jetwhale.host.settings.licenses_version
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwDialog
import com.kitakkun.jetwhale.host.ui.JwHorizontalDivider
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwIconButton
import com.kitakkun.jetwhale.host.ui.JwKeyValueRow
import com.kitakkun.jetwhale.host.ui.JwListItem
import com.kitakkun.jetwhale.host.ui.JwMetrics
import com.kitakkun.jetwhale.host.ui.JwShapes
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwSurface
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwToolbar
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import org.jetbrains.compose.resources.stringResource

/** The share of the window the licenses dialog takes. */
private const val LICENSES_DIALOG_WINDOW_FRACTION = 0.8f

/** The tallest a license text grows inside its dialog before it scrolls. */
private val LicenseTextMaxHeight = 320.dp

/**
 * Every third-party library the host ships, one row each, with its licenses as tags. Clicking a row
 * opens the library's details and the full license text.
 */
@Composable
fun LicensesScreen(
    libraries: Libs,
    onClickBack: () -> Unit,
) {
    var selectedLibrary by remember { mutableStateOf<Library?>(null) }
    JwSurface(
        modifier = Modifier.fillMaxSize(LICENSES_DIALOG_WINDOW_FRACTION),
        color = JwTheme.colors.elevatedBackground,
        shape = JwShapes.large,
        border = BorderStroke(JwMetrics.borderWidth, JwTheme.colors.border),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            JwToolbar(
                title = stringResource(Res.string.licenses_title),
                navigationIcon = {
                    JwIconButton(onClick = onClickBack, tooltip = stringResource(Res.string.licenses_back)) {
                        JwIcon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
            JwHorizontalDivider()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(JwSpacing.medium),
            ) {
                items(libraries.libraries, key = { it.uniqueId }) { library ->
                    JwListItem(
                        text = library.name,
                        supportingText = library.artifactId,
                        selected = library == selectedLibrary,
                        onClick = { selectedLibrary = library },
                        trailingContent = {
                            library.licenses.forEach { license -> JwTag(text = license.spdxId ?: license.name) }
                        },
                    )
                }
            }
        }
    }
    selectedLibrary?.let { library ->
        LibraryDetailDialog(library = library, onDismissRequest = { selectedLibrary = null })
    }
}

/** The details of one library: version, a link to its website, and each license's text. */
@Composable
private fun LibraryDetailDialog(
    library: Library,
    onDismissRequest: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    JwDialog(
        onDismissRequest = onDismissRequest,
        title = library.name,
        closeLabel = stringResource(Res.string.close),
        confirmButton = library.website?.let { website ->
            {
                JwButton(
                    text = stringResource(Res.string.licenses_open_website),
                    onClick = { uriHandler.openUri(website) },
                    style = JwButtonStyle.Text,
                )
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(JwSpacing.medium),
        ) {
            JwKeyValueRow(
                key = stringResource(Res.string.licenses_version),
                value = library.artifactVersion.orEmpty(),
                monospace = true,
            )
            library.description?.takeIf { it.isNotBlank() }?.let { description ->
                JwText(text = description, style = JwTheme.textStyles.bodySmall, color = JwTheme.colors.textSecondary)
            }
            library.licenses.forEach { license ->
                JwText(text = license.name, style = JwTheme.textStyles.subtitle)
                val content = license.licenseContent?.takeIf { it.isNotBlank() }
                    ?: license.url
                    ?: stringResource(Res.string.licenses_no_license_text)
                // Bounded and scrolled on its own so a long license keeps the dialog's buttons in reach.
                JwText(
                    text = content,
                    style = JwTheme.textStyles.bodySmall,
                    color = JwTheme.colors.textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = LicenseTextMaxHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(end = JwSpacing.small),
                )
            }
        }
    }
}
