package com.kitakkun.jetwhale.plugins.nav3.agent

import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.agent.sdk.JetWhaleAgentPlugin
import com.kitakkun.jetwhale.plugins.nav3.protocol.BackStackChanged
import com.kitakkun.jetwhale.plugins.nav3.protocol.BackStackUnregistered
import com.kitakkun.jetwhale.plugins.nav3.protocol.DEFAULT_NAV_STACK_ID
import com.kitakkun.jetwhale.plugins.nav3.protocol.GetNavState
import com.kitakkun.jetwhale.plugins.nav3.protocol.MutateBackStack
import com.kitakkun.jetwhale.plugins.nav3.protocol.MutationResult
import com.kitakkun.jetwhale.plugins.nav3.protocol.NAV3_PLUGIN_ID
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavBackStackSnapshot
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavKeySnapshot
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavState
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessageHandlers
import com.kitakkun.jetwhale.protocol.messaging.reply
import com.kitakkun.jetwhale.protocol.messaging.trySend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Agent plugin that puts an app's Navigation 3 back stacks under the host's control.
 *
 * Register the back stack the app already has and the plugin does the rest: it mirrors every change
 * to the host, and applies the pushes, pops and replacements the host (or an AI agent driving it
 * over MCP) asks for.
 *
 * ```kotlin
 * val module = SerializersModule { polymorphic(NavKey::class) { subclass(Home::class, Home.serializer()) } }
 * val nav3Plugin = JetWhaleNav3AgentPlugin(Nav3KeyCodec.openPolymorphic(module))
 *
 * startJetWhale { plugins { register(nav3Plugin) } }
 *
 * @Composable
 * fun App() {
 *     val backStack = rememberNavBackStack(SavedStateConfiguration { serializersModule = module }, Home)
 *     nav3Plugin.TrackNavBackStack(backStack)
 *     NavDisplay(backStack = backStack, ...)
 * }
 * ```
 *
 * An app with nested navigation can register several stacks under different ids; the host then
 * picks which one to act on.
 *
 * @param codec Turns the app's keys into JSON and back — see [Nav3KeyCodec].
 */
class JetWhaleNav3AgentPlugin<K : NavKey>(
    private val codec: Nav3KeyCodec<K>,
) : JetWhaleAgentPlugin() {
    override val pluginId: String get() = NAV3_PLUGIN_ID
    override val pluginVersion: String get() = "1.0.0"

    private val backStacks = MutableStateFlow<Map<String, MutableList<K>>>(emptyMap())

    // Non-null exactly while the host has this plugin activated, so it doubles as the "may send
    // events" gate: a stack registered before activation is picked up when observation starts.
    private var observationScope: CoroutineScope? = null

    /**
     * Starts mirroring [backStack] to the host under [stackId].
     *
     * Prefer [TrackNavBackStack] from a composable; call this directly only when the stack's
     * lifetime is managed outside composition — and then pair it with [unregisterBackStack].
     *
     * Takes any [MutableList] so it fits both a `NavBackStack` and the `SnapshotStateList` an app
     * may drive `NavDisplay` with directly. Changes are observed through the snapshot system, so a
     * list that is not snapshot-backed would only ever report its initial contents — but such a
     * list would already keep `NavDisplay` itself from recomposing, so it is not a state a working
     * app can be in.
     *
     * @param stackId Names the stack for the host. The default suits the common single-stack app;
     *   an app with nested navigation gives each stack its own id.
     */
    fun registerBackStack(backStack: MutableList<K>, stackId: String = DEFAULT_NAV_STACK_ID) {
        backStacks.update { it + (stackId to backStack) }
    }

    /** Stops mirroring the stack registered as [stackId] and tells the host it is gone. */
    fun unregisterBackStack(stackId: String) {
        val previous = backStacks.getAndUpdate { it - stackId }
        if (stackId in previous && observationScope != null) {
            // A stack leaving composition is only news while someone is watching; dropping it when
            // offline is fine because the host asks for the full state on reconnect.
            messenger.trySend(BackStackUnregistered(stackId))
        }
    }

    override fun JetWhaleMessageHandlers.configure() {
        // The host asks for this once per connection: the events below only report changes, and the
        // key catalog never travels any other way.
        onRequest { _: GetNavState ->
            reply(
                NavState(
                    stacks = backStacks.value.map { (stackId, backStack) -> snapshotOf(stackId, backStack.toList()) },
                    keyTypes = codec.keyTypes,
                ),
            )
        }
        onRequest { request: MutateBackStack -> reply(mutate(request)) }
    }

    override fun onActivate() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        observationScope = scope
        scope.launch {
            // Re-collected whenever a stack is registered or unregistered; each collection emits
            // the stack's current contents first, so registering is itself reported to the host.
            backStacks.collectLatest { current ->
                coroutineScope {
                    current.forEach { (stackId, backStack) ->
                        launch {
                            snapshotFlow { backStack.toList() }.collect { keys ->
                                messenger.trySend(BackStackChanged(snapshotOf(stackId, keys)))
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDeactivate() {
        observationScope?.cancel()
        observationScope = null
    }

    private fun mutate(request: MutateBackStack): MutationResult {
        val registered = backStacks.value
        val backStack = registered[request.stackId] ?: return MutationResult(
            error = "no back stack is registered as '${request.stackId}'; registered ids: ${registered.keys.joinToString().ifEmpty { "none" }}",
            snapshot = null,
        )
        return try {
            // A mutable snapshot makes the whole edit land as one change — one recomposition, and
            // nothing applied at all if a later operation turns out to be invalid.
            Snapshot.withMutableSnapshot { applyNavOperations(backStack, request.operations, codec::decode) }
            MutationResult(error = null, snapshot = snapshotOf(request.stackId, backStack.toList()))
        } catch (e: IllegalArgumentException) {
            // Covers both invalid operations and undecodable keys (SerializationException is one).
            MutationResult(
                error = e.message ?: "the back stack operations could not be applied",
                snapshot = snapshotOf(request.stackId, backStack.toList()),
            )
        }
    }

    private fun snapshotOf(stackId: String, keys: List<K>): NavBackStackSnapshot = NavBackStackSnapshot(
        stackId = stackId,
        entries = keys.map { key ->
            val encoded = codec.encode(key)
            NavKeySnapshot(
                typeName = encoded.typeNameOrNull() ?: key::class.simpleName ?: "NavKey",
                display = key.toString(),
                key = encoded,
            )
        },
    )
}

/** The serial name an encoded key carries as its discriminator, which is what the catalog names it by. */
private fun JsonElement?.typeNameOrNull(): String? = ((this as? JsonObject)?.get(NAV_KEY_DISCRIMINATOR) as? JsonPrimitive)?.content
