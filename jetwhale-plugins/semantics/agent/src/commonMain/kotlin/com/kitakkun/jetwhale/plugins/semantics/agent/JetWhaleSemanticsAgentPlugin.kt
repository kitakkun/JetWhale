package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.agent.sdk.JetWhaleAgentPlugin
import com.kitakkun.jetwhale.plugins.semantics.protocol.CaptureNodeTree
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeRoot
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeSnapshot
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessageHandlers
import com.kitakkun.jetwhale.protocol.messaging.reply
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock
import kotlin.time.TimeSource

/**
 * Platform-agnostic core of the Compose Semantics Inspector agent plugin.
 *
 * It answers two host requests — capture the semantics tree, and invoke one node's action — by
 * delegating to the [ComposeNodeSource]s registered in [ComposeNodeSourceRegistry]. Register the
 * plugin with the agent runtime, and install a platform probe so the registry has roots to read:
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
    }

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

        return NodeTreeSnapshot(
            capturedAtMs = Clock.System.now().toEpochMilliseconds(),
            captureDurationMs = started.elapsedNow().inWholeMilliseconds,
            options = options,
            roots = roots,
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
