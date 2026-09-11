package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.agent.sdk.JetWhaleAgentPlugin
import com.kitakkun.jetwhale.plugins.semantics.protocol.CaptureNodeTree
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeRoot
import com.kitakkun.jetwhale.plugins.semantics.protocol.GetViewAttributes
import com.kitakkun.jetwhale.plugins.semantics.protocol.HighlightNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeHitTesting
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeSnapshot
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.SetViewAttribute
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessageHandlers
import com.kitakkun.jetwhale.protocol.messaging.reply
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.TimeSource

/**
 * Platform-agnostic core of the Compose Semantics Inspector agent plugin.
 *
 * It answers host requests — capture the semantics tree, invoke one node's action, read or write a
 * `View` node's platform attributes, and draw a box over one node on the device — by delegating to
 * the [ComposeNodeSource]s registered in [ComposeNodeSourceRegistry]. The last two need the optional
 * [ViewAttributeSource] and [NodeHighlightSource] capabilities, which only the Android window source
 * has. Register the plugin with the agent runtime, and install a platform probe so the registry has
 * roots to read:
 *
 * ```kotlin
 * // Android, Application.onCreate()
 * installJetWhaleSemanticsProbe(this)
 * startJetWhale {
 *     plugins { register(JetWhaleSemanticsAgentPlugin()) }
 * }
 * ```
 *
 * With no probe installed the plugin still answers, reporting an empty tree and a warning saying
 * so — a connected host then shows why it sees nothing instead of silently showing nothing.
 */
class JetWhaleSemanticsAgentPlugin : JetWhaleAgentPlugin() {
    override val pluginId: String get() = PLUGIN_ID
    override val pluginVersion: String get() = "1.0.0"

    override fun JetWhaleMessageHandlers.configure() {
        onRequest { request: CaptureNodeTree ->
            reply(capture(request.options))
        }
        onRequest { request: PerformNodeAction ->
            reply(performAction(request))
        }
        onRequest { request: GetViewAttributes ->
            reply(readViewAttributes(request))
        }
        onRequest { request: SetViewAttribute ->
            reply(writeViewAttribute(request))
        }
        onRequest { request: HighlightNode ->
            // A plugin disabled and re-enabled in quick succession would otherwise race its own
            // teardown: the clear it started on deactivation could land after this request and wipe
            // the box just drawn. Waiting for it keeps the two in the order they were asked in.
            teardown?.join()
            // Requests are dispatched concurrently, and each one hops to the app's main thread, so
            // two in flight could finish in either order and leave the box on the node the host asked
            // for first. The lock hands them to the overlay in the order they arrived.
            reply(highlightMutex.withLock { showHighlight(request) })
        }
    }

    // A highlight is drawn into the app's own window, so it must not outlive the host that asked for
    // it: a dropped connection and a disabled plugin both take it down. The agent-side TTL stays as
    // the net for a host that dies without either happening.
    override suspend fun onDisconnected() {
        highlightMutex.withLock { clearAllHighlights() }
    }

    override fun onDeactivate() {
        // Deactivation is not a suspending hook and clearing hops to the app's UI thread, so it runs
        // on a scope of its own rather than blocking the runtime's teardown. Only the next highlight
        // request waits on it.
        teardown = teardownScope.launch { highlightMutex.withLock { clearAllHighlights() } }
    }

    private val teardownScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var teardown: Job? = null
    private val highlightMutex = Mutex()

    private suspend fun capture(options: NodeTreeCaptureOptions): NodeTreeSnapshot {
        val started = TimeSource.Monotonic.markNow()
        val sources = ComposeNodeSourceRegistry.sources
        val roots = mutableListOf<ComposeRoot>()
        val warnings = mutableListOf<String>()

        if (sources.isEmpty()) warnings += NO_PROBE_WARNING

        for (source in sources) {
            try {
                source.capture(options)?.let(roots::add)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // One unreadable root (a view detached mid-capture, a toolkit-specific failure)
                // must not cost the caller the roots that did read cleanly.
                warnings += "${source.sourceId}: failed to capture (${e.describe()})"
            }
        }

        // Only here are all the windows in hand, and a tap is dispatched across the whole stack of
        // them: a dialog decides what can be touched in the window underneath it.
        val hitTested = NodeHitTesting.resolve(roots)

        return NodeTreeSnapshot(
            capturedAtMs = Clock.System.now().toEpochMilliseconds(),
            captureDurationMs = started.elapsedNow().inWholeMilliseconds,
            options = options,
            roots = hitTested,
            warnings = warnings,
        )
    }

    private suspend fun performAction(request: PerformNodeAction): NodeActionResult {
        val source = ComposeNodeSourceRegistry.sources.firstOrNull { it.sourceId == request.rootId }
            ?: return NodeActionResult(
                performed = false,
                message = "unknown rootId: ${request.rootId} (the root may have been detached; capture the tree again)",
            )
        return try {
            source.performAction(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            NodeActionResult(performed = false, message = "action failed: ${e.describe()}")
        }
    }

    private fun Throwable.describe(): String = message?.takeIf { it.isNotBlank() } ?: (this::class.simpleName ?: "unknown error")

    companion object {
        const val PLUGIN_ID: String = "com.kitakkun.jetwhale.semantics"

        internal const val NO_PROBE_WARNING: String =
            "No Compose root is registered. Install a probe in the app: installJetWhaleSemanticsProbe(application) " +
                "on Android, or call JetWhaleSemanticsProbe() inside your composition."
    }
}
