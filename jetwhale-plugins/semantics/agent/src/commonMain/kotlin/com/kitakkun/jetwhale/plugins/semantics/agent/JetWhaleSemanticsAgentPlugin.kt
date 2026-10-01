package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.agent.sdk.JetWhaleAgentPlugin
import com.kitakkun.jetwhale.plugins.semantics.protocol.CaptureNodeTree
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeRoot
import com.kitakkun.jetwhale.plugins.semantics.protocol.GetViewAttributes
import com.kitakkun.jetwhale.plugins.semantics.protocol.HighlightNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.HighlightResult
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
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
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
 * // iOS, at startup on the main thread
 * installJetWhaleSemanticsProbe()
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

    // The host's plugin-manifest.json accepts only the agent versions inside its agentVersionRange,
    // so bump that range together with this.
    override val pluginVersion: String get() = "1.1.0"

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
            // A clear started by a quick disable and re-enable could otherwise land after this
            // request and wipe the box just drawn.
            teardown?.join()
            reply(highlightMutex.withLock { highlight(request) })
        }
    }

    override suspend fun onDisconnected() {
        highlightMutex.withLock { clearAllHighlights() }
    }

    override fun onDeactivate() {
        teardown = teardownScope.launch { highlightMutex.withLock { clearAllHighlights() } }
    }

    private val teardownScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
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
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                warnings += "${source.sourceId}: failed to capture (${e.describeFailure()})"
            }
        }

        val hitTested = NodeHitTesting.resolve(roots)

        return NodeTreeSnapshot(
            capturedAtMs = Clock.System.now().toEpochMilliseconds(),
            captureDurationMs = started.elapsedNow().inWholeMilliseconds,
            options = options,
            roots = hitTested,
            warnings = warnings,
        )
    }

    /** Shows one node on the device, or clears the root's highlight, saying why when it cannot. */
    private suspend fun highlight(request: HighlightNode): HighlightResult {
        val source = ComposeNodeSourceRegistry.sourceOf(request.rootId)
            ?: return HighlightResult(shown = false, message = ComposeNodeSourceRegistry.unknownRootMessage(request.rootId))
        val highlightSource = source as? NodeHighlightSource
            ?: return HighlightResult(shown = false, message = ROOT_WITHOUT_HIGHLIGHT)
        if (request.nodeId != null && request.ttlMs <= 0) {
            return HighlightResult(shown = false, message = "ttlMs must be positive to show a highlight, but was ${request.ttlMs}")
        }
        @Suppress("KOTRAIL_CATCH_TOO_BROAD")
        return try {
            highlightSource.highlight(nodeId = request.nodeId, ttl = request.ttlMs.milliseconds)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            HighlightResult(shown = false, message = "highlighting failed: ${e.describeFailure()}")
        }
    }

    /**
     * Takes every highlight down.
     *
     * The box belongs to a host that is looking at the tree, so it must not outlive one that has
     * stopped. The per-root TTL stays regardless — it is the net for a host that dies without saying
     * so.
     */
    private suspend fun clearAllHighlights() {
        for (source in ComposeNodeSourceRegistry.sources) {
            val highlightSource = source as? NodeHighlightSource ?: continue
            @Suppress("KOTRAIL_CATCH_TOO_BROAD")
            try {
                highlightSource.highlight(nodeId = null, ttl = Duration.ZERO)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
            }
        }
    }

    private suspend fun performAction(request: PerformNodeAction): NodeActionResult {
        val source = ComposeNodeSourceRegistry.sourceOf(request.rootId)
            ?: return NodeActionResult(performed = false, message = ComposeNodeSourceRegistry.unknownRootMessage(request.rootId))
        return try {
            source.performAction(request)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            NodeActionResult(performed = false, message = "action failed: ${e.describeFailure()}")
        }
    }

    companion object {
        const val PLUGIN_ID: String = "com.kitakkun.jetwhale.semantics"

        private const val ROOT_WITHOUT_HIGHLIGHT: String = "this root cannot be highlighted (it is a composition read through its SemanticsOwner, which has no window to draw in)"

        internal const val NO_PROBE_WARNING: String =
            "No root is registered. Install a probe in the app: installJetWhaleSemanticsProbe(application) " +
                "on Android, installJetWhaleSemanticsProbe() on iOS, or call JetWhaleSemanticsProbe() inside your composition."
    }
}
