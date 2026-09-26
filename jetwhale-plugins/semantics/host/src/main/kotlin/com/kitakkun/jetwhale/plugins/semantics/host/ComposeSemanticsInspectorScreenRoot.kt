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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
    var merged by rememberPersistent("merged-tree", default = true)
    var interactiveOnly by rememberPersistent("interactive-only", default = false)
    var includeInvisible by rememberPersistent("include-invisible", default = false)
    var autoRefresh by rememberPersistent("auto-refresh", default = false)
    // Off by default, deliberately: the box is drawn into the app itself, so it would otherwise turn
    // up in any `screencap` taken while the inspector is open — a QA run's screenshots included.
    var highlightOnDevice by rememberPersistent("highlight-on-device", default = false)
    var selectedKey by remember { mutableStateOf<NodeKey?>(null) }
    var hoveredKey by remember { mutableStateOf<NodeKey?>(null) }
    val scope = rememberCoroutineScope()

    val options = NodeTreeCaptureOptions(merged = merged, includeInvisible = includeInvisible, maxDepth = null)

    // Capture once when the screen opens, and again whenever an option changes what would be
    // captured — an option the user toggled should show its effect without a second click.
    LaunchedEffect(options) {
        onCapture(options)
    }
    val currentOnCapture by rememberUpdatedState(onCapture)
    LaunchedEffect(autoRefresh, options) {
        if (!autoRefresh) return@LaunchedEffect
        while (true) {
            delay(AUTO_REFRESH_INTERVAL_MILLIS)
            // Awaited, not fired and forgotten: captures are serialised on the app's main thread,
            // so a fixed-interval loop against a slow app would queue requests faster than they
            // drain and leave the view showing an ever-older tree.
            currentOnCapture(options)
        }
    }
    LaunchedEffect(selectedKey) {
        onSelectedNodeChange(selectedKey)
    }

    // Hover wins over selection while the pointer is on a row — "which one is this?" is the question
    // being asked at that moment — and the selection is still there when the pointer leaves.
    val highlighted = if (highlightOnDevice) hoveredKey ?: selectedKey else null
    // Sending the target, holding it against the app's timeout and taking it down again all outlive
    // this composition, so they are the plugin's; which node to point at is the view's answer.
    LaunchedEffect(highlighted) { onHighlightTargetChange(highlighted) }
    val currentOnHighlightTargetChange by rememberUpdatedState(onHighlightTargetChange)
    DisposableEffect(Unit) {
        onDispose { currentOnHighlightTargetChange(null) }
    }

    ComposeSemanticsInspectorScreen(
        snapshot = snapshot,
        capturing = capturing,
        roundTripMs = roundTripMs,
        errorMessage = errorMessage,
        actionStatus = actionStatus,
        highlightStatus = highlightStatus,
        viewAttributes = viewAttributes,
        flags = SemanticsInspectorFlags(
            merged = merged,
            interactiveOnly = interactiveOnly,
            includeInvisible = includeInvisible,
            autoRefresh = autoRefresh,
            highlightOnDevice = highlightOnDevice,
        ),
        selectedKey = selectedKey,
        onRefresh = { scope.launch { onCapture(options) } },
        onFlagsChange = { flags ->
            merged = flags.merged
            interactiveOnly = flags.interactiveOnly
            includeInvisible = flags.includeInvisible
            autoRefresh = flags.autoRefresh
            highlightOnDevice = flags.highlightOnDevice
        },
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
