package com.kitakkun.jetwhale.plugins.deeplinks.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwFormField
import com.kitakkun.jetwhale.host.ui.JwListItem
import com.kitakkun.jetwhale.host.ui.JwSectionHeader
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwTagStyle
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTextField
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkTemplate

/** The link being composed, as the composer shows it. */
internal data class LinkComposerState(
    val template: DeepLinkTemplate?,
    val draftUrl: String,
    val parameters: Map<String, String>,
    val check: DraftCheck,
)

@Composable
internal fun LinkComposer(
    state: LinkComposerState,
    history: List<OpenedLink>,
    favorites: List<String>,
    canOpen: Boolean,
    actions: DeepLinkActions,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(JwSpacing.large), verticalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
        val template = state.template
        if (template == null) {
            JwFormField(label = "Link") {
                JwTextField(value = state.draftUrl, onValueChange = actions::editUrl, placeholder = "myapp://item/42", modifier = Modifier.fillMaxWidth())
            }
        } else {
            JwText(text = template.name, style = JwTheme.textStyles.title)
            JwText(text = template.template, style = JwTheme.textStyles.code, color = JwTheme.colors.textSecondary)
            state.parameters.forEach { (name, value) ->
                JwFormField(label = name) {
                    JwTextField(value = value, onValueChange = { actions.editParameter(name, it) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        DraftStatus(state.check)
        Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.small)) {
            val url = state.check.url
            JwButton(text = "Open in app", onClick = { url?.let(actions::open) }, style = JwButtonStyle.Primary, enabled = url != null && canOpen)
            JwButton(
                text = if (url != null && url in favorites) "Unstar" else "Star",
                onClick = { url?.let(onToggleFavorite) },
                enabled = url != null,
            )
        }
        JwSectionHeader(title = "History", count = history.size, contentPadding = PaddingValues())
        if (history.isEmpty()) {
            JwText(text = "Links you open appear here.", color = JwTheme.colors.textSecondary)
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(history) { entry -> HistoryRow(entry, actions) }
            }
        }
    }
}

@Composable
private fun DraftStatus(check: DraftCheck) {
    val problem = check.problem
    when {
        problem != null -> JwText(text = problem, color = JwTheme.colors.textSecondary)

        check.matches.isEmpty() -> JwText(
            text = "No declared link matches; the app may still route it, but the platform will not send it here from outside.",
            color = JwTone.Warning.color,
        )

        else -> JwText(text = "Matches ${check.matches.joinToString { it.handler.substringAfterLast('.') }}", color = JwTone.Success.color)
    }
}

@Composable
private fun HistoryRow(entry: OpenedLink, actions: DeepLinkActions) {
    val result = entry.result
    JwListItem(selected = false, onClick = { actions.editUrl(entry.url) }) {
        JwTag(text = if (result.opened) "opened" else "failed", style = JwTagStyle.Tinted, tone = if (result.opened) JwTone.Success else JwTone.Error)
        Column(Modifier.weight(1f)) {
            JwText(text = entry.url, style = JwTheme.textStyles.code)
            val detail = result.error ?: result.handledBy.joinToString { it.substringAfterLast('.') }.takeIf(String::isNotEmpty)
            detail?.let { JwText(text = it, style = JwTheme.textStyles.labelSmall, color = JwTheme.colors.textSecondary) }
        }
        JwButton(text = "Open again", onClick = { actions.open(entry.url) }, style = JwButtonStyle.Text)
    }
}
