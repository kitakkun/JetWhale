package com.kitakkun.jetwhale.plugins.semantics.host

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.sdk.rememberPersistent
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwCheckbox
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwHorizontalDivider
import com.kitakkun.jetwhale.host.ui.JwKeyValueRow
import com.kitakkun.jetwhale.host.ui.JwProgressIndicator
import com.kitakkun.jetwhale.host.ui.JwSearchField
import com.kitakkun.jetwhale.host.ui.JwSectionHeader
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwSplitPane
import com.kitakkun.jetwhale.host.ui.JwStatusLine
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTextField
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwTreeRow
import com.kitakkun.jetwhale.host.ui.LocalJwContentColor
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState
import com.kitakkun.jetwhale.plugins.semantics.protocol.AppleNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeRoot
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeSnapshot
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.UiNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttribute
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.advertisedAs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.datatransfer.StringSelection
import kotlin.math.roundToInt

/**
 * Browser for the Compose node tree of the running app: the tree on the left, the selected node's
 * semantics and the actions it exposes on the right.
 *
 * Captures are pull-based — a capture reads the debuggee's semantics on its main thread, so the app
 * pays only when someone is looking. Auto-refresh exists for watching a screen change, but is off by
 * default for the same reason.
 *
 * Everything that reaches beyond the composition is owned here — the toggles that outlive the host
 * run, and the captures they ask for — leaving [ComposeSemanticsInspectorScreen] a pure function of
 * its arguments. Which node is selected is decided here too, in the tree, and reported upward rather
 * than asked for: what a selection is worth reading from the app is the plugin's decision. Which node
 * the pointer rests on is the same kind of answer, and the two together decide what the device is
 * asked to point at.
 */
@Composable
internal fun ComposeSemanticsInspectorScreenRoot(
    snapshot: NodeTreeSnapshot?,
    capturing: Boolean,
    roundTripMs: Long?,
    errorMessage: String?,
    actionStatus: String?,
    highlightStatus: String?,
    viewAttributes: ViewAttributesUiState,
    onCapture: suspend (NodeTreeCaptureOptions) -> Unit,
    onPerformAction: (PerformNodeAction) -> Unit,
    onSelectedNodeChange: (NodeKey?) -> Unit,
    onHighlightTargetChange: (NodeKey?) -> Unit,
    onCommitViewAttribute: (ViewAttribute, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val persistentFlags = rememberPersistentInspectorFlags()
    val flags = persistentFlags.flags
    var selectedKey by remember { mutableStateOf<NodeKey?>(null) }
    var hoveredKey by remember { mutableStateOf<NodeKey?>(null) }
    val scope = rememberCoroutineScope()

    val options = NodeTreeCaptureOptions(merged = flags.merged, includeInvisible = flags.includeInvisible, maxDepth = null)

    LaunchedEffect(options) {
        onCapture(options)
    }
    val currentOnCapture by rememberUpdatedState(onCapture)
    LaunchedEffect(flags.autoRefresh, options) {
        if (!flags.autoRefresh) return@LaunchedEffect
        while (true) {
            delay(AUTO_REFRESH_INTERVAL_MILLIS)
            currentOnCapture(options)
        }
    }
    LaunchedEffect(selectedKey) {
        onSelectedNodeChange(selectedKey)
    }

    DeviceHighlightEffect(
        highlightTarget = { if (persistentFlags.flags.highlightOnDevice) hoveredKey ?: selectedKey else null },
        onHighlightTargetChange = onHighlightTargetChange,
    )

    ComposeSemanticsInspectorScreen(
        snapshot = snapshot,
        capturing = capturing,
        roundTripMs = roundTripMs,
        errorMessage = errorMessage,
        actionStatus = actionStatus,
        highlightStatus = highlightStatus,
        viewAttributes = viewAttributes,
        flags = flags,
        selectedKey = selectedKey,
        onRefresh = { scope.launch { onCapture(options) } },
        onFlagsChange = { persistentFlags.flags = it },
        onSelectKey = { selectedKey = it },
        onHoverChange = { key, hovered ->
            // A row that reports leaving must not clear a hover another row has already taken
            // over — the pointer arrives before the old row lets go.
            hoveredKey = if (hovered) key else hoveredKey.takeIf { it != key }
        },
        onPerformAction = onPerformAction,
        onCommitViewAttribute = onCommitViewAttribute,
        modifier = modifier,
    )
}

private const val AUTO_REFRESH_INTERVAL_MILLIS = 1_000L

/** The inspector's toggles, each stored for the next host run under the key it has always had. */
@Stable
private class PersistentInspectorFlags(
    private val merged: MutableState<Boolean>,
    private val interactiveOnly: MutableState<Boolean>,
    private val includeInvisible: MutableState<Boolean>,
    private val autoRefresh: MutableState<Boolean>,
    private val highlightOnDevice: MutableState<Boolean>,
) {
    var flags: SemanticsInspectorFlags
        get() = SemanticsInspectorFlags(
            merged = merged.value,
            interactiveOnly = interactiveOnly.value,
            includeInvisible = includeInvisible.value,
            autoRefresh = autoRefresh.value,
            highlightOnDevice = highlightOnDevice.value,
        )
        set(value) {
            merged.value = value.merged
            interactiveOnly.value = value.interactiveOnly
            includeInvisible.value = value.includeInvisible
            autoRefresh.value = value.autoRefresh
            highlightOnDevice.value = value.highlightOnDevice
        }
}

@Composable
private fun rememberPersistentInspectorFlags(): PersistentInspectorFlags {
    val merged = rememberPersistent("merged-tree", default = true)
    val interactiveOnly = rememberPersistent("interactive-only", default = false)
    val includeInvisible = rememberPersistent("include-invisible", default = false)
    val autoRefresh = rememberPersistent("auto-refresh", default = false)
    val highlightOnDevice = rememberPersistent("highlight-on-device", default = false)
    return remember(merged, interactiveOnly, includeInvisible, autoRefresh, highlightOnDevice) {
        PersistentInspectorFlags(
            merged = merged,
            interactiveOnly = interactiveOnly,
            includeInvisible = includeInvisible,
            autoRefresh = autoRefresh,
            highlightOnDevice = highlightOnDevice,
        )
    }
}

/** Points the device at [highlightTarget] while the inspector is shown, and at nothing once it leaves. */
@Composable
private fun DeviceHighlightEffect(
    highlightTarget: () -> NodeKey?,
    onHighlightTargetChange: (NodeKey?) -> Unit,
) {
    val currentHighlightTarget by rememberUpdatedState(highlightTarget)
    val currentOnHighlightTargetChange by rememberUpdatedState(onHighlightTargetChange)
    // Read in the effect, not in composition, so a pointer crossing rows doesn't recompose the screen.
    LaunchedEffect(Unit) {
        snapshotFlow { currentHighlightTarget() }
            .collect { currentOnHighlightTargetChange(it) }
    }
    DisposableEffect(Unit) {
        onDispose { currentOnHighlightTargetChange(null) }
    }
}
