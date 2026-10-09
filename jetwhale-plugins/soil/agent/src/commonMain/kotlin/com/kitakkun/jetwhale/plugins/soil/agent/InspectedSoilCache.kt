@file:OptIn(InternalSoilQueryApi::class, ExperimentalSoilQueryApi::class)

package com.kitakkun.jetwhale.plugins.soil.agent

import com.kitakkun.jetwhale.plugins.soil.protocol.SoilCacheCoverage
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryKind
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryLocation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import soil.query.InfiniteQueryId
import soil.query.MutationOptions
import soil.query.QueryOptions
import soil.query.SubscriptionCache
import soil.query.SubscriptionOptions
import soil.query.SwrCacheInternal
import soil.query.SwrCachePlusInternal
import soil.query.SwrCachePlusPolicy
import soil.query.SwrCachePlusView
import soil.query.SwrCachePolicy
import soil.query.SwrCacheView
import soil.query.SwrClient
import soil.query.annotation.ExperimentalSoilQueryApi
import soil.query.annotation.InternalSoilQueryApi
import soil.query.core.Reply
import soil.query.core.UniqueId
import soil.query.withQuery
import soil.query.withSubscription

/**
 * The app's Soil cache, read and acted on the way the plugin needs.
 *
 * Soil has no public way to list what it holds, so entries are read through its internal store
 * views, which only `SwrCache` and `SwrCachePlus` implement. The stores are plain maps that Soil
 * changes on the policy's main dispatcher, so every read and removal happens there too; without a
 * policy that is `Dispatchers.Main`, Soil's own default.
 */
internal class InspectedSoilCache(
    private val client: SwrClient,
    private val policy: SwrCachePolicy?,
) {
    private val cacheView: SwrCacheView? = client as? SwrCacheView
    private val subscriptionView: SwrCachePlusView? = client as? SwrCachePlusView
    private val subscriptionCache: SubscriptionCache? = (policy as? SwrCachePlusPolicy)?.subscriptionCache
    private val mainDispatcher: CoroutineDispatcher = policy?.mainDispatcher ?: Dispatchers.Main

    val coverage: SoilCacheCoverage = SoilCacheCoverage(
        clientClassName = client::class.simpleName ?: "SwrClient",
        isClientReadable = cacheView != null,
        includesInactiveEntries = cacheView != null && policy != null,
        includesSubscriptions = subscriptionView != null,
    )

    /** Every entry Soil holds; none when the client is not one of Soil's own caches. */
    suspend fun readRecords(): List<SoilCacheRecord> {
        val view = cacheView ?: return emptyList()
        return withContext(mainDispatcher) {
            buildList {
                addQueries(view)
                addMutations(view)
                subscriptionView?.let { addSubscriptions(it) }
            }
        }
    }

    /** The entry's last reply, or null when Soil no longer holds the entry. */
    suspend fun readReply(key: SoilEntryKey): Reply<*>? {
        val view = cacheView ?: return null
        return withContext(mainDispatcher) {
            when (key.kind) {
                SoilEntryKind.QUERY, SoilEntryKind.INFINITE_QUERY -> (view.queryStoreView[key.id]?.state?.value ?: policy?.queryCache?.get(key.id))?.reply
                SoilEntryKind.MUTATION -> view.mutationStoreView[key.id]?.state?.value?.reply
                SoilEntryKind.SUBSCRIPTION -> (subscriptionView?.subscriptionStoreView?.get(key.id)?.state?.value ?: subscriptionCache?.get(key.id))?.reply
            }
        }
    }

    /** Invalidates the query [key] names, active or inactive, through Soil's public effect API. */
    suspend fun invalidate(key: SoilEntryKey) {
        client.effect { withQuery { invalidateQueriesBy(key.id) } }.join()
    }

    /** Resumes the active query or subscription [key] names, through Soil's public effect API. */
    suspend fun resume(key: SoilEntryKey) {
        when (key.kind) {
            SoilEntryKind.SUBSCRIPTION -> client.effect { withSubscription { resumeSubscriptionsBy(key.id) } }.join()
            else -> client.effect { withQuery { resumeQueriesBy(key.id) } }.join()
        }
    }

    /**
     * Drops the inactive query or subscription [key] names from the policy's cache.
     *
     * Soil's own `removeQueriesBy` also cancels an active entry while screens still hold it, so the
     * cache entry is deleted directly, and only after checking on the main dispatcher that the
     * entry has not become active since the host saw it.
     *
     * @return Why nothing was removed, or null when the entry was removed.
     */
    suspend fun removeInactive(key: SoilEntryKey): String? {
        val view = cacheView ?: return "The client is not one Soil Inspector can read."
        return withContext(mainDispatcher) {
            when (key.kind) {
                SoilEntryKind.QUERY, SoilEntryKind.INFINITE_QUERY -> {
                    val queryCache = policy?.queryCache
                    when {
                        queryCache == null -> NO_POLICY_REFUSAL

                        key.id in view.queryStoreView -> BECAME_ACTIVE_REFUSAL

                        else -> {
                            queryCache.delete(key.id)
                            null
                        }
                    }
                }

                SoilEntryKind.SUBSCRIPTION -> when {
                    subscriptionCache == null -> NO_POLICY_REFUSAL

                    subscriptionView?.subscriptionStoreView?.containsKey(key.id) == true -> BECAME_ACTIVE_REFUSAL

                    else -> {
                        subscriptionCache.delete(key.id)
                        null
                    }
                }

                SoilEntryKind.MUTATION -> "Soil keeps no inactive mutations."
            }
        }
    }

    private fun MutableList<SoilCacheRecord>.addQueries(view: SwrCacheView) {
        val active = view.queryStoreView
        val queryCache = policy?.queryCache
        active.forEach { (id, query) ->
            add(
                SoilCacheRecord(
                    key = SoilEntryKey(kind = queryKindOf(id), id = id),
                    location = if (queryCache?.get(id) != null) SoilEntryLocation.ACTIVE_AND_CACHED else SoilEntryLocation.ACTIVE,
                    model = query.state.value,
                    isObserved = query.hasAttachedInstances(),
                    options = (query as? SwrCacheInternal.ManagedQuery<*>)?.options?.describe().orEmpty(),
                    stateFlow = query.state,
                ),
            )
        }
        queryCache?.keys?.toList()?.forEach { id ->
            if (id in active) return@forEach
            val state = queryCache[id] ?: return@forEach
            add(SoilCacheRecord(key = SoilEntryKey(kind = queryKindOf(id), id = id), location = SoilEntryLocation.INACTIVE, model = state, isObserved = false, options = emptyMap(), stateFlow = null))
        }
    }

    private fun MutableList<SoilCacheRecord>.addMutations(view: SwrCacheView) {
        view.mutationStoreView.forEach { (id, mutation) ->
            add(
                SoilCacheRecord(
                    key = SoilEntryKey(kind = SoilEntryKind.MUTATION, id = id),
                    location = SoilEntryLocation.ACTIVE,
                    model = mutation.state.value,
                    isObserved = mutation.hasAttachedInstances(),
                    options = (mutation as? SwrCacheInternal.ManagedMutation<*>)?.options?.describe().orEmpty(),
                    stateFlow = mutation.state,
                ),
            )
        }
    }

    private fun MutableList<SoilCacheRecord>.addSubscriptions(view: SwrCachePlusView) {
        val active = view.subscriptionStoreView
        active.forEach { (id, subscription) ->
            add(
                SoilCacheRecord(
                    key = SoilEntryKey(kind = SoilEntryKind.SUBSCRIPTION, id = id),
                    location = if (subscriptionCache?.get(id) != null) SoilEntryLocation.ACTIVE_AND_CACHED else SoilEntryLocation.ACTIVE,
                    model = subscription.state.value,
                    isObserved = subscription.hasAttachedInstances(),
                    options = (subscription as? SwrCachePlusInternal.ManagedSubscription<*>)?.options?.describe().orEmpty(),
                    stateFlow = subscription.state,
                ),
            )
        }
        subscriptionCache?.keys?.toList()?.forEach { id ->
            if (id in active) return@forEach
            val state = subscriptionCache[id] ?: return@forEach
            add(SoilCacheRecord(key = SoilEntryKey(kind = SoilEntryKind.SUBSCRIPTION, id = id), location = SoilEntryLocation.INACTIVE, model = state, isObserved = false, options = emptyMap(), stateFlow = null))
        }
    }
}

private const val NO_POLICY_REFUSAL = "Inactive entries are only reachable when the app hands its SwrCachePolicy to the plugin."

private const val BECAME_ACTIVE_REFUSAL = "The entry has become active, and active entries are not removed."

private fun queryKindOf(id: UniqueId): SoilEntryKind = if (id is InfiniteQueryId<*, *>) SoilEntryKind.INFINITE_QUERY else SoilEntryKind.QUERY

private fun QueryOptions.describe(): Map<String, String> = mapOf(
    "staleTime" to staleTime.toString(),
    "gcTime" to gcTime.toString(),
    "keepAliveTime" to keepAliveTime.toString(),
    "prefetchWindowTime" to prefetchWindowTime.toString(),
    "revalidateOnReconnect" to revalidateOnReconnect.toString(),
    "revalidateOnFocus" to revalidateOnFocus.toString(),
    "retryCount" to retryCount.toString(),
)

private fun MutationOptions.describe(): Map<String, String> = mapOf(
    "isOneShot" to isOneShot.toString(),
    "isStrictMode" to isStrictMode.toString(),
    "shouldExecuteEffectSynchronously" to shouldExecuteEffectSynchronously.toString(),
    "keepAliveTime" to keepAliveTime.toString(),
    "retryCount" to retryCount.toString(),
)

private fun SubscriptionOptions.describe(): Map<String, String> = mapOf(
    "gcTime" to gcTime.toString(),
    "keepAliveTime" to keepAliveTime.toString(),
    "restartOnReconnect" to restartOnReconnect.toString(),
    "restartOnFocus" to restartOnFocus.toString(),
    "retryCount" to retryCount.toString(),
)
