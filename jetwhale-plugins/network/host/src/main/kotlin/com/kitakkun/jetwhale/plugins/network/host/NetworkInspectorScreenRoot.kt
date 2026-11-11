package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.sdk.rememberPersistent
import com.kitakkun.jetwhale.host.ui.JwSplitPaneState
import com.kitakkun.jetwhale.host.ui.JwTab
import com.kitakkun.jetwhale.host.ui.JwTabRow
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpResponse
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatchType
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatcher
import com.kitakkun.jetwhale.plugins.network.protocol.MockResponseSpec
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The plugin's entry point: the one piece of state only a running host can supply — the split
 * position the user dragged, kept in the plugin's storage — is owned here, so
 * [NetworkInspectorScreen] is a pure function of its arguments.
 */
@Composable
fun NetworkInspectorScreenRoot(
    transactions: List<HttpTransaction>,
    mockRules: List<MockRule>,
    mockingEnabled: Boolean,
    onClearTransactions: () -> Unit,
    onToggleMocking: (Boolean) -> Unit,
    onMockRulesChanged: (List<MockRule>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trafficSplitPaneState = rememberPersistedSplitPaneState()

    NetworkInspectorScreen(
        transactions = transactions,
        mockRules = mockRules,
        mockingEnabled = mockingEnabled,
        trafficSplitPaneState = trafficSplitPaneState,
        onClearTransactions = onClearTransactions,
        onToggleMocking = onToggleMocking,
        onMockRulesChanged = onMockRulesChanged,
        modifier = modifier,
    )
}

/**
 * The split position the user dragged, kept across host restarts.
 *
 * rememberPersistent hydrates from disk asynchronously, i.e. after the split state has already been
 * constructed, so the two are mirrored in both directions rather than seeded once. Both are backed
 * by snapshot state with structural equality, so echoing an unchanged value back does not re-emit
 * and the mirroring settles immediately.
 */
@Composable
private fun rememberPersistedSplitPaneState(): JwSplitPaneState {
    var storedSplitPosition by rememberPersistent(SPLIT_POSITION_KEY, DEFAULT_SPLIT_POSITION)
    val splitPaneState = rememberJwSplitPaneState(DEFAULT_SPLIT_POSITION)
    LaunchedEffect(splitPaneState) {
        launch {
            snapshotFlow { storedSplitPosition }
                .collect { splitPaneState.fraction = it }
        }
        snapshotFlow { splitPaneState.fraction }
            .collect { storedSplitPosition = it }
    }
    return splitPaneState
}

private const val SPLIT_POSITION_KEY = "traffic.splitPosition"

private const val DEFAULT_SPLIT_POSITION = 0.42f
