package com.kitakkun.jetwhale.plugins.nav3.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMessagingHostPlugin
import com.kitakkun.jetwhale.plugins.nav3.protocol.BackStackChanged
import com.kitakkun.jetwhale.plugins.nav3.protocol.BackStackUnregistered
import com.kitakkun.jetwhale.plugins.nav3.protocol.GetNavState
import com.kitakkun.jetwhale.plugins.nav3.protocol.MutateBackStack
import com.kitakkun.jetwhale.plugins.nav3.protocol.MutationResult
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavBackStackOperation
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavBackStackSnapshot
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavKeyTypeDescriptor
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessageHandlers
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import com.kitakkun.jetwhale.protocol.messaging.request
import kotlinx.coroutines.launch

// Instantiated by the host via the fully-qualified name declared in plugin-manifest.json.
@Suppress("UNUSED")
class Nav3HostPluginFactory : JetWhaleHostPluginFactory {
    override fun createPlugin(): JetWhaleHostPlugin = Nav3HostPlugin()
}

@OptIn(ExperimentalJetWhaleApi::class)
private class Nav3HostPlugin :
    JetWhaleMessagingHostPlugin(),
    JetWhaleHostPluginUi,
    JetWhaleMcpCapablePlugin,
    Nav3BackStackController {

    private val stacks: SnapshotStateList<NavBackStackSnapshot> = mutableStateListOf()
    private var navKeyTypes: List<NavKeyTypeDescriptor> by mutableStateOf(emptyList())
    private var selectedStackId: String? by mutableStateOf(null)
    private var status: Nav3Status? by mutableStateOf(null)

    override fun JetWhaleMessageHandlers.configure() {
        onEvent { event: BackStackChanged -> applySnapshot(event.snapshot) }
        onEvent { event: BackStackUnregistered ->
            stacks.removeAll { it.stackId == event.stackId }
            if (selectedStackId == event.stackId) selectedStackId = stacks.firstOrNull()?.stackId
        }
    }

    // The events above only carry changes, and the key catalog travels nowhere else, so the whole
    // state is fetched once per connection before any handler runs.
    override suspend fun onPrepare() {
        val state = messenger.request(GetNavState)
        stacks.apply {
            clear()
            addAll(state.stacks)
        }
        navKeyTypes = state.keyTypes
        selectedStackId = state.stacks.firstOrNull()?.stackId
    }

    private fun applySnapshot(snapshot: NavBackStackSnapshot) {
        val index = stacks.indexOfFirst { it.stackId == snapshot.stackId }
        if (index >= 0) stacks[index] = snapshot else stacks.add(snapshot)
        if (selectedStackId == null) selectedStackId = snapshot.stackId
    }

    // -------------------------------------------------------------------------
    // Nav3BackStackController — the state and the one mutating call the UI and the MCP
    // commands both work through.
    // -------------------------------------------------------------------------

    override fun stacks(): List<NavBackStackSnapshot> = stacks.toList()

    override fun keyTypes(): List<NavKeyTypeDescriptor> = navKeyTypes

    override suspend fun mutate(stackId: String, operations: List<NavBackStackOperation>): MutationResult = try {
        val result = messenger.request(MutateBackStack(stackId = stackId, operations = operations))
        // The agent also broadcasts the change, but adopting the reply keeps the UI in step even if
        // the event is still in flight.
        result.snapshot?.let(::applySnapshot)
        result
    } catch (e: JetWhaleMessagingException) {
        MutationResult(error = "failed to reach the debuggee: ${e.message}", snapshot = null)
    }

    // -------------------------------------------------------------------------
    // JetWhaleHostPluginUi
    // -------------------------------------------------------------------------

    @Composable
    override fun Content() {
        Nav3NavigatorScreen(
            stacks = stacks,
            keyTypes = navKeyTypes,
            selectedStackId = selectedStackId,
            status = status,
            onSelectStack = { selectedStackId = it },
            onApplyOperation = { stackId, operation -> applyFromUi(stackId, operation) },
            onRefresh = {
                pluginScope.launch {
                    status = try {
                        val state = messenger.request(GetNavState)
                        stacks.apply {
                            clear()
                            addAll(state.stacks)
                        }
                        navKeyTypes = state.keyTypes
                        if (stacks.none { it.stackId == selectedStackId }) selectedStackId = stacks.firstOrNull()?.stackId
                        Nav3Status(message = "Reloaded from the app.", isError = false)
                    } catch (e: JetWhaleMessagingException) {
                        Nav3Status(message = "Failed to reach the debuggee: ${e.message}", isError = true)
                    }
                }
            },
        )
    }

    private fun applyFromUi(stackId: String, operation: NavBackStackOperation) {
        pluginScope.launch {
            val result = mutate(stackId, listOf(operation))
            status = when (val error = result.error) {
                null -> Nav3Status(message = "${operation.describe()} applied.", isError = false)
                else -> Nav3Status(message = error, isError = true)
            }
        }
    }

    // -------------------------------------------------------------------------
    // JetWhaleMcpCapablePlugin
    // -------------------------------------------------------------------------

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
        GetBackStackCommand(this),
        ListNavKeyTypesCommand(this),
        PushNavKeyCommand(this),
        PopBackStackCommand(this),
        RemoveNavKeyCommand(this),
        MoveNavKeyToTopCommand(this),
        ReplaceBackStackCommand(this),
    )
}
