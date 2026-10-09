package com.kitakkun.jetwhale.plugins.soil.agent

import com.kitakkun.jetwhale.agent.sdk.JetWhaleAgentPlugin
import com.kitakkun.jetwhale.plugins.soil.protocol.GetSoilCacheSnapshot
import com.kitakkun.jetwhale.plugins.soil.protocol.GetSoilEntryValue
import com.kitakkun.jetwhale.plugins.soil.protocol.RunSoilEntryAction
import com.kitakkun.jetwhale.plugins.soil.protocol.SOIL_PLUGIN_ID
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryAction
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryActionPolicy
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryActionResult
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryValue
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessageHandlers
import com.kitakkun.jetwhale.protocol.messaging.reply
import com.kitakkun.jetwhale.protocol.messaging.trySend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import soil.query.SwrCachePolicy
import soil.query.SwrClient

/**
 * Agent plugin that shows the host what an app's Soil cache holds — queries, infinite queries,
 * mutations and subscriptions, with their state and values — and lets it invalidate, resume, or
 * remove inactive entries.
 *
 * ```kotlin
 * val policy = SwrCachePolicy(coroutineScope = SwrCacheScope())
 * val swrClient = SwrCache(policy)
 *
 * startJetWhale { plugins { register(JetWhaleSoilAgentPlugin(swrClient, policy)) } }
 * ```
 *
 * The plugin reads Soil through its internal API, which may change between Soil releases; it is
 * built and tested against the Soil version it depends on.
 *
 * @param client The app's client. Only Soil's own `SwrCache` and `SwrCachePlus` can be read; for
 *   any other client the host says it is unsupported.
 * @param policy The policy [client] was built with, or null. With it, the host also sees the
 *   entries Soil keeps after their last user went away, and the plugin reads the cache on the
 *   policy's `mainDispatcher`. Without it, only active entries are shown, read on
 *   `Dispatchers.Main`.
 * @param valueSerializers Serializers for values whose classes the plugin cannot find one for, such
 *   as generic ones.
 */
class JetWhaleSoilAgentPlugin(
    client: SwrClient,
    policy: SwrCachePolicy?,
    valueSerializers: SoilValueSerializers = SoilValueSerializers.None,
) : JetWhaleAgentPlugin() {
    override val pluginId: String get() = SOIL_PLUGIN_ID

    // The host's plugin-manifest.json accepts only the agent versions inside its agentVersionRange,
    // so bump that range together with this.
    override val pluginVersion: String get() = "1.0.0"

    private val cache = InspectedSoilCache(client, policy)
    private val reporter = SoilCacheReporter(cache, SoilEntryHandles())
    private val valueEncoder = SoilValueEncoder(valueSerializers)

    /** Whether a connected host has taken a snapshot, so that changes are worth reading for it. */
    private val isHostFollowingChanges = MutableStateFlow(false)

    private var reportingScope: CoroutineScope? = null

    override fun JetWhaleMessageHandlers.configure() {
        onRequest { _: GetSoilCacheSnapshot ->
            val snapshot = reporter.takeSnapshot()
            isHostFollowingChanges.value = cache.coverage.isClientReadable
            reply(snapshot)
        }
        onRequest { request: GetSoilEntryValue -> reply(readValue(request.handle)) }
        onRequest { request: RunSoilEntryAction -> reply(runAction(request.handle, request.action)) }
    }

    override fun onActivate() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        reportingScope = scope
        scope.launch {
            isHostFollowingChanges.collectLatest { following ->
                if (following) reporter.reportChanges { changes -> messenger.trySend(changes) }
            }
        }
    }

    override suspend fun onDisconnected() {
        isHostFollowingChanges.value = false
    }

    override fun onDeactivate() {
        isHostFollowingChanges.value = false
        reportingScope?.cancel()
        reportingScope = null
    }

    private suspend fun readValue(handle: String): SoilEntryValue {
        val key = reporter.keyOf(handle) ?: return SoilEntryValue.EntryGone
        val reply = cache.readReply(key) ?: return SoilEntryValue.EntryGone
        return withContext(Dispatchers.Default) { valueEncoder.encode(key, reply) }
    }

    private suspend fun runAction(handle: String, action: SoilEntryAction): SoilEntryActionResult {
        val key = reporter.keyOf(handle)
        val entry = key?.let { reporter.entryOf(it) }
        if (key == null || entry == null) return SoilEntryActionResult(error = "No entry has the handle '$handle' any more.")
        SoilEntryActionPolicy.refusalOf(entry, action)?.let { return SoilEntryActionResult(error = it) }
        when (action) {
            SoilEntryAction.INVALIDATE -> cache.invalidate(key)
            SoilEntryAction.RESUME -> cache.resume(key)
            SoilEntryAction.REMOVE_INACTIVE -> cache.removeInactive(key)?.let { return SoilEntryActionResult(error = it) }
        }
        reporter.requestReading()
        return SoilEntryActionResult(error = null)
    }
}
