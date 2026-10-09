package com.kitakkun.jetwhale.plugins.soil.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.kitakkun.jetwhale.host.ui.JwBanner
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwListItem
import com.kitakkun.jetwhale.host.ui.JwSearchField
import com.kitakkun.jetwhale.host.ui.JwSectionHeader
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwSplitPane
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwTagStyle
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwToolbar
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilCacheCoverage
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryKind
import kotlinx.coroutines.delay

/** The list is a column of ids and badges; the state, options and value beside it need the room. */
private const val LIST_FRACTION = 0.42f

/** Relative times in the UI move on by the second. */
private const val CLOCK_TICK_MILLIS = 1_000L

/** Binds the live [browser] and a ticking clock to [SoilInspectorScreen]. */
@Composable
internal fun SoilInspectorScreenRoot(browser: SoilCacheBrowser, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    val agentNowEpochSeconds by produceState(browser.agentNowEpochSeconds()) {
        while (true) {
            delay(CLOCK_TICK_MILLIS)
            value = browser.agentNowEpochSeconds()
        }
    }
    SoilInspectorScreen(
        coverage = browser.coverage,
        listedEntries = browser.listedEntries,
        selectedEntry = browser.selectedEntry,
        selectedValue = browser.selectedValue,
        status = browser.status,
        query = query,
        agentNowEpochSeconds = agentNowEpochSeconds,
        actions = browser,
        onQueryChange = { query = it },
        modifier = modifier,
    )
}

@Composable
internal fun SoilInspectorScreen(
    coverage: SoilCacheCoverage?,
    listedEntries: List<ListedSoilEntry>,
    selectedEntry: ListedSoilEntry?,
    selectedValue: SoilValueLoad?,
    status: SoilBrowserStatus?,
    query: String,
    agentNowEpochSeconds: Long,
    actions: SoilInspectorActions,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        JwToolbar(
            title = "Soil Inspector",
            actions = { JwButton(text = "Reload from app", onClick = actions::refresh, style = JwButtonStyle.Text) },
        )
        status?.let { JwBanner(text = it.message, tone = if (it.isError) JwTone.Error else JwTone.Neutral) }
        when {
            coverage == null -> JwEmptyState(title = "Waiting for the app", description = "The Soil cache shows up once the app's agent answers.")

            !coverage.isClientReadable -> JwEmptyState(
                title = "Unsupported client",
                description = "The app handed over a ${coverage.clientClassName}. Soil Inspector reads Soil's own SwrCache and SwrCachePlus; pass one of those, not a client that wraps it.",
            )

            else -> {
                if (!coverage.includesInactiveEntries) {
                    JwBanner(text = "Only active entries are shown. Pass the SwrCachePolicy to JetWhaleSoilAgentPlugin to see the inactive ones Soil keeps cached.", tone = JwTone.Info)
                }
                JwSplitPane(
                    state = rememberJwSplitPaneState(LIST_FRACTION),
                    first = {
                        SoilEntryListPane(
                            listedEntries = listedEntries,
                            selectedHandle = selectedEntry?.entry?.handle,
                            query = query,
                            agentNowEpochSeconds = agentNowEpochSeconds,
                            onQueryChange = onQueryChange,
                            onSelect = actions::select,
                        )
                    },
                    second = {
                        Box(Modifier.fillMaxSize()) {
                            if (selectedEntry == null) {
                                JwEmptyState(title = "Nothing selected", description = "Pick an entry to see its state, options and value.")
                            } else {
                                SoilEntryDetailPane(
                                    listed = selectedEntry,
                                    value = selectedValue,
                                    agentNowEpochSeconds = agentNowEpochSeconds,
                                    onRunAction = actions::runActionOnSelected,
                                    onReloadValue = actions::reloadSelectedValue,
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoilEntryListPane(
    listedEntries: List<ListedSoilEntry>,
    selectedHandle: String?,
    query: String,
    agentNowEpochSeconds: Long,
    onQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    val matching = listedEntries.filter { it.matches(query) }
    Column(Modifier.fillMaxSize()) {
        JwSearchField(
            value = query,
            clearLabel = "Clear search",
            onValueChange = onQueryChange,
            placeholder = "Search namespaces and tags",
            modifier = Modifier.fillMaxWidth().padding(JwSpacing.medium),
        )
        if (matching.isEmpty()) {
            JwEmptyState(
                title = if (listedEntries.isEmpty()) "The cache is empty" else "No matches",
                description = if (listedEntries.isEmpty()) "Entries appear as the app uses its queries, mutations and subscriptions." else "No namespace or tag contains \"$query\".",
            )
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            SoilEntryKind.entries.forEach { kind ->
                val group = matching.filter { it.entry.kind == kind }
                if (group.isEmpty()) return@forEach
                item(key = kind.name) { JwSectionHeader(title = kind.pluralLabel, count = group.size) }
                items(group, key = { it.entry.handle }) { listed ->
                    JwListItem(selected = listed.entry.handle == selectedHandle, onClick = { onSelect(listed.entry.handle) }, muted = listed.isGone) {
                        Column(Modifier.weight(1f).padding(vertical = JwSpacing.small), verticalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.small), verticalAlignment = Alignment.CenterVertically) {
                                JwText(text = listed.entry.id.namespace, style = JwTheme.textStyles.code, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                if (listed.entry.id.tags.isNotEmpty()) {
                                    JwText(text = listed.entry.id.tags.joinToString(prefix = "[", postfix = "]"), style = JwTheme.textStyles.bodySmall, color = JwTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall), verticalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall)) {
                                badgesOf(listed, agentNowEpochSeconds).forEach { JwTag(text = it.text, tone = it.tone, style = JwTagStyle.Tinted) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ListedSoilEntry.matches(query: String): Boolean = query.isBlank() ||
    entry.id.namespace.contains(query, ignoreCase = true) ||
    entry.id.tags.any { it.contains(query, ignoreCase = true) }
