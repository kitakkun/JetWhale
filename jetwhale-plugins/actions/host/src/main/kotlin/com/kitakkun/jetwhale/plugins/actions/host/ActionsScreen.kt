package com.kitakkun.jetwhale.plugins.actions.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.kitakkun.jetwhale.host.sdk.rememberPersistent
import com.kitakkun.jetwhale.host.ui.JwBanner
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwSplitPane
import com.kitakkun.jetwhale.host.ui.JwTab
import com.kitakkun.jetwhale.host.ui.JwTabRow
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwToolbar
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionCatalog
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** The action list is a column of names; the form and results beside it need the room. */
private const val LIST_FRACTION = 0.35f

/**
 * Binds the host-owned state — the browser, the pins and the arguments each action last ran with,
 * both persisted across restarts — to [ActionsScreen].
 */
@Composable
internal fun ActionsScreenRoot(browser: ActionsBrowser, modifier: Modifier = Modifier) {
    var pinned by rememberPersistent("pinned-actions", default = emptyList<String>())
    var lastArguments by rememberPersistent("last-arguments", default = emptyMap<String, String>())
    var query by remember { mutableStateOf("") }
    val rememberedArguments = remember(lastArguments) { lastArguments.mapValues { (_, text) -> Json.parseToJsonElement(text).jsonObject } }
    ActionsScreen(
        catalog = browser.catalog,
        selectedAction = browser.selectedAction,
        tab = browser.tab,
        history = browser.history,
        selectedRunId = browser.selectedRunId,
        prefill = browser.prefill,
        options = browser.options,
        status = browser.status,
        query = query,
        pinnedIds = pinned.toSet(),
        rememberedArguments = rememberedArguments,
        actions = browser,
        onQueryChange = { query = it },
        onTogglePin = { id -> pinned = if (id in pinned) pinned - id else pinned + id },
        onRun = { id, arguments, confirmedDestructive ->
            lastArguments = lastArguments + (id to arguments.toString())
            browser.run(id, arguments, confirmedDestructive)
        },
        modifier = modifier,
    )
}

@Composable
internal fun ActionsScreen(
    catalog: ActionCatalog?,
    selectedAction: ActionDescriptor?,
    tab: ActionsTab,
    history: List<RunRecord>,
    selectedRunId: String?,
    prefill: FormPrefill?,
    options: Map<String, Map<String, List<String>>>,
    status: ActionsStatus?,
    query: String,
    pinnedIds: Set<String>,
    rememberedArguments: Map<String, JsonObject>,
    actions: ActionsScreenActions,
    onQueryChange: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onRun: (actionId: String, arguments: JsonObject, confirmedDestructive: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val searchFocus = remember { FocusRequester() }
    Column(
        modifier.fillMaxSize().onPreviewKeyEvent { event ->
            // The search field exists only while the Actions tab lists actions.
            val searchShown = tab == ActionsTab.ACTIONS && !catalog?.actions.isNullOrEmpty()
            val isPaletteShortcut = searchShown && event.type == KeyEventType.KeyDown && event.key == Key.K && (event.isMetaPressed || event.isCtrlPressed)
            if (isPaletteShortcut) searchFocus.requestFocus()
            isPaletteShortcut
        },
    ) {
        JwToolbar(
            title = "Debug Actions",
            actions = { JwButton(text = "Reload from app", onClick = actions::refresh, style = JwButtonStyle.Text) },
        )
        status?.let { JwBanner(text = it.message, tone = if (it.isError) JwTone.Error else JwTone.Neutral) }
        JwTabRow {
            JwTab(selected = tab == ActionsTab.ACTIONS, text = "Actions", onClick = { actions.showTab(ActionsTab.ACTIONS) })
            JwTab(selected = tab == ActionsTab.HISTORY, text = "History", count = history.size, onClick = { actions.showTab(ActionsTab.HISTORY) })
        }
        when (tab) {
            ActionsTab.ACTIONS -> ActionsTabContent(
                catalog = catalog,
                selectedAction = selectedAction,
                history = history,
                prefill = prefill,
                options = options,
                query = query,
                searchFocus = searchFocus,
                pinnedIds = pinnedIds,
                rememberedArguments = rememberedArguments,
                actions = actions,
                onQueryChange = onQueryChange,
                onTogglePin = onTogglePin,
                onRun = onRun,
            )

            ActionsTab.HISTORY -> RunHistoryPane(
                runs = history,
                selectedRunId = selectedRunId,
                onSelect = actions::selectRun,
                onRunAgain = actions::runAgain,
            )
        }
    }
}

@Composable
private fun ActionsTabContent(
    catalog: ActionCatalog?,
    selectedAction: ActionDescriptor?,
    history: List<RunRecord>,
    prefill: FormPrefill?,
    options: Map<String, Map<String, List<String>>>,
    query: String,
    searchFocus: FocusRequester,
    pinnedIds: Set<String>,
    rememberedArguments: Map<String, JsonObject>,
    actions: ActionsScreenActions,
    onQueryChange: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onRun: (actionId: String, arguments: JsonObject, confirmedDestructive: Boolean) -> Unit,
) {
    if (catalog?.actions.isNullOrEmpty()) {
        JwEmptyState(
            title = "No debug actions",
            description = "The app has registered none. Register actions with JetWhaleDebugActionsAgentPlugin.register { }.",
        )
        return
    }
    JwSplitPane(
        state = rememberJwSplitPaneState(LIST_FRACTION),
        first = {
            ActionListPane(
                actions = searchActions(catalog.actions, query, pinnedIds),
                query = query,
                searchFocus = searchFocus,
                pinnedIds = pinnedIds,
                selectedId = selectedAction?.id,
                onQueryChange = onQueryChange,
                onSelect = actions::select,
                onTogglePin = onTogglePin,
            )
        },
        second = {
            Box(Modifier.fillMaxSize()) {
                if (selectedAction == null) {
                    JwEmptyState(title = "Nothing selected", description = "Pick an action to see its arguments and run it.")
                } else {
                    val prefilled = prefill?.takeIf { it.actionId == selectedAction.id }
                    // A new prefill starts the form over, even when it carries the same arguments as before.
                    key(prefilled) {
                        ActionDetailPane(
                            action = selectedAction,
                            options = options[selectedAction.id].orEmpty(),
                            rememberedArguments = prefilled?.arguments ?: rememberedArguments[selectedAction.id],
                            runs = history.filter { it.actionId == selectedAction.id },
                            onRun = { arguments, confirmedDestructive -> onRun(selectedAction.id, arguments, confirmedDestructive) },
                            onCancel = actions::cancel,
                        )
                    }
                }
            }
        },
    )
}
