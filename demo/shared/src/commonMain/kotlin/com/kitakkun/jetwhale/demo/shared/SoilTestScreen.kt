package com.kitakkun.jetwhale.demo.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import soil.query.annotation.ExperimentalSoilQueryApi
import soil.query.compose.SwrClientProvider
import soil.query.compose.rememberInfiniteQuery
import soil.query.compose.rememberMutation
import soil.query.compose.rememberQuery
import soil.query.compose.rememberSubscription

/**
 * Queries, an infinite query, a mutation and a subscription, all through the
 * Soil client the Soil Inspector watches. Leaving the tab makes them inactive after Soil's
 * keepAliveTime, and the cached ones stay in the inspector until their gcTime runs out.
 */
@OptIn(ExperimentalSoilQueryApi::class)
@Composable
internal fun SoilTestScreen() {
    SwrClientProvider(DIModule.swrClient) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Text("Watch this tab's queries, mutation and subscription in the host's Soil Inspector.") }
            item { ProfileSection() }
            item { SlowReportSection() }
            item { FeedSection() }
            item { ClockSection() }
            item { EnvelopeSection() }
        }
    }
}

@Composable
private fun ProfileSection() {
    val profile = rememberQuery(remember { DemoProfileQueryKey() })
    val rename = rememberMutation(remember { DemoRenameProfileMutationKey() })
    val failingQuery = rememberQuery(remember { DemoFailingQueryKey() })
    val scope = rememberCoroutineScope()
    Section(title = "Query and mutation") {
        Text("Profile: ${profile.data ?: profile.status.name}")
        Text("Rename: ${rename.status.name}, ${rename.mutatedCount} run(s)")
        Text("Failing query: ${failingQuery.error?.message ?: failingQuery.status.name}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { scope.launch { rename.mutate(if (profile.data?.name == "Ada Lovelace") "Grace Hopper" else "Ada Lovelace") } }) {
                Text("Rename")
            }
            OutlinedButton(onClick = { scope.launch { profile.refresh() } }) {
                Text("Refresh")
            }
        }
    }
}

/** Hidden until asked for, so its fifteen-second fetch starts when the button is pressed. */
@Composable
private fun SlowReportSection() {
    var isShown by remember { mutableStateOf(false) }
    Section(title = "Slow query") {
        if (isShown) {
            val report = rememberQuery(demoSlowReportQueryKey)
            Text("Report: ${report.data ?: report.status.name}")
        }
        OutlinedButton(onClick = { isShown = !isShown }) {
            Text(if (isShown) "Hide slow report" else "Load slow report")
        }
    }
}

/** Hiding the feed lets go of its query, which Soil then moves into the cache. */
@Composable
private fun FeedSection() {
    var isShown by remember { mutableStateOf(true) }
    Section(title = "Infinite query") {
        if (isShown) FeedPages()
        OutlinedButton(onClick = { isShown = !isShown }) {
            Text(if (isShown) "Hide feed" else "Show feed")
        }
    }
}

@Composable
private fun FeedPages() {
    val feed = rememberInfiniteQuery(demoFeedQueryKey)
    val scope = rememberCoroutineScope()
    Text("Pages: ${feed.data?.size ?: 0}, posts: ${feed.data?.sumOf { it.data.posts.size } ?: 0}")
    val nextPage = feed.loadMoreParam
    Button(onClick = { nextPage?.let { scope.launch { feed.loadMore(it) } } }, enabled = nextPage != null) {
        Text(if (nextPage != null) "Load page $nextPage" else "All pages loaded")
    }
}

@OptIn(ExperimentalSoilQueryApi::class)
@Composable
private fun ClockSection() {
    val clock = rememberSubscription(demoClockSubscriptionKey)
    Section(title = "Subscription") {
        Text("Ticks: ${clock.data?.count ?: clock.status.name}")
    }
}

@Composable
private fun EnvelopeSection() {
    val envelope = rememberQuery(demoEnvelopeQueryKey)
    val registeredReceipt = rememberQuery(remember { DemoReceiptQueryKey(DemoReceiptQueryKey.REGISTERED_NAMESPACE) })
    val unregisteredReceipt = rememberQuery(remember { DemoReceiptQueryKey(DemoReceiptQueryKey.UNREGISTERED_NAMESPACE) })
    Section(title = "Values for the inspector to encode") {
        Text("Generic envelope: ${envelope.data?.payload?.title ?: envelope.status.name}")
        Text("Receipt, serializer registered: ${registeredReceipt.data ?: registeredReceipt.status.name}")
        Text("Receipt, no serializer: ${unregisteredReceipt.data ?: unregisteredReceipt.status.name}")
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Preview
@Composable
private fun SoilTestScreenPreview() {
    SoilTestScreen()
}
