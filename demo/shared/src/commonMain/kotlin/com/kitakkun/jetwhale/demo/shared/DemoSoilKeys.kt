package com.kitakkun.jetwhale.demo.shared

import com.kitakkun.jetwhale.plugins.soil.agent.SoilValueSerializers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import soil.query.InfiniteQueryId
import soil.query.InfiniteQueryKey
import soil.query.MutationId
import soil.query.MutationKey
import soil.query.QueryId
import soil.query.QueryKey
import soil.query.QueryOptionsOverride
import soil.query.SubscriptionId
import soil.query.SubscriptionKey
import soil.query.buildInfiniteQueryKey
import soil.query.buildMutationKey
import soil.query.buildQueryKey
import soil.query.buildSubscriptionKey
import soil.query.copy
import soil.query.core.Effect
import soil.query.withQuery
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Serializable
internal data class DemoProfile(val id: Int, val name: String, val fetchCount: Int)

@Serializable
internal data class DemoPost(val id: Int, val title: String)

@Serializable
internal data class DemoPostPage(val posts: List<DemoPost>, val page: Int)

@Serializable
internal data class DemoTick(val count: Int)

/** Generic, so no serializer can be looked up for it: the Soil Inspector needs one registered. */
@Serializable
internal data class DemoEnvelope<T>(val payload: T, val servedBy: String)

/** The serializers the demo hands the Soil Inspector for what it cannot encode by itself. */
internal val demoSoilValueSerializers: SoilValueSerializers = SoilValueSerializers {
    namespace(DemoEnvelopeQueryKey.REGISTERED_NAMESPACE, DemoEnvelope.serializer(DemoPost.serializer()))
}

/** The profile as the demo's fake backend holds it; the rename mutation changes it. */
private var demoProfileName = "Ada Lovelace"
private var demoProfileFetchCount = 0

/** A query whose data goes stale after ten seconds, so the inspector's Stale badge shows up. */
internal class DemoProfileQueryKey :
    QueryKey<DemoProfile> by buildQueryKey(
        id = ID,
        fetch = {
            delay(800.milliseconds)
            DemoProfile(id = 42, name = demoProfileName, fetchCount = ++demoProfileFetchCount)
        },
    ) {
    override fun onConfigureOptions(): QueryOptionsOverride = { options -> options.copy(staleTime = 10.seconds) }

    companion object {
        val ID = QueryId<DemoProfile>("demo/profile", 42)
    }
}

/** Renames the profile, then invalidates its query so it refetches. */
internal class DemoRenameProfileMutationKey :
    MutationKey<DemoProfile, String> by buildMutationKey(
        id = MutationId("demo/rename-profile"),
        mutate = { name ->
            delay(600.milliseconds)
            demoProfileName = name
            DemoProfile(id = 42, name = name, fetchCount = demoProfileFetchCount)
        },
    ) {
    override fun onMutateEffect(variable: String, data: DemoProfile): Effect = {
        withQuery { invalidateQueriesBy(DemoProfileQueryKey.ID) }
    }
}

/** Five pages of three posts. */
internal val demoFeedQueryKey: InfiniteQueryKey<DemoPostPage, Int> = buildInfiniteQueryKey(
    id = InfiniteQueryId("demo/feed"),
    fetch = { page ->
        delay(500.milliseconds)
        DemoPostPage(posts = (1..3).map { DemoPost(id = page * 3 + it, title = "Post ${page * 3 + it}") }, page = page)
    },
    initialParam = { 0 },
    loadMoreParam = { chunks -> (chunks.last().param + 1).takeIf { it < 5 } },
)

/** A query that always fails, to show an error in the inspector. */
internal val demoFlakyQueryKey: QueryKey<String> = buildQueryKey(
    id = QueryId("demo/flaky"),
    fetch = {
        delay(300.milliseconds)
        error("The demo server is down")
    },
)

/** Ticks once a second while observed. */
internal val demoClockSubscriptionKey: SubscriptionKey<DemoTick> = buildSubscriptionKey(
    id = SubscriptionId("demo/clock"),
    subscribe = {
        flow<DemoTick> {
            var count = 0
            while (true) {
                emit(DemoTick(count++))
                delay(1.seconds)
            }
        }
    },
)

/**
 * The same generic value under two namespaces: [REGISTERED_NAMESPACE] has a serializer in
 * [demoSoilValueSerializers], the other falls back to `toString()` in the inspector.
 */
internal class DemoEnvelopeQueryKey(namespace: String) :
    QueryKey<DemoEnvelope<DemoPost>> by buildQueryKey(
        id = QueryId(namespace),
        fetch = {
            delay(400.milliseconds)
            DemoEnvelope(payload = DemoPost(id = 1, title = "Hello, Soil"), servedBy = "demo-backend")
        },
    ) {
    companion object {
        const val REGISTERED_NAMESPACE = "demo/envelope/registered"
        const val UNREGISTERED_NAMESPACE = "demo/envelope/unregistered"
    }
}
