package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpResponse
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatchType
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatcher
import com.kitakkun.jetwhale.plugins.network.protocol.MockResponseSpec
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule
import java.util.UUID

@Composable
fun NetworkInspectorScreen(
    transactions: List<HttpTransaction>,
    mockRules: List<MockRule>,
    mockingEnabled: Boolean,
    onClearTransactions: () -> Unit,
    onToggleMocking: (Boolean) -> Unit,
    onMockRulesChanged: (List<MockRule>) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Traffic (${transactions.size})") })
            Tab(selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Mocks (${mockRules.size})") })
        }
        when (selectedTab) {
            0 -> TrafficTab(
                transactions = transactions,
                onClear = onClearTransactions,
                onCreateMock = { tx ->
                    tx.response?.let { response ->
                        onMockRulesChanged(mockRules + mockRuleFrom(tx, response))
                        selectedTab = 1
                    }
                },
            )

            else -> MocksTab(mockRules, mockingEnabled, onToggleMocking, onMockRulesChanged)
        }
    }
}

/** Builds a mock rule pre-filled from a real captured transaction's response. */
private fun mockRuleFrom(tx: HttpTransaction, response: CapturedHttpResponse): MockRule {
    val contentType = response.headers.entries
        .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
        ?.value?.firstOrNull()
    return MockRule(
        id = UUID.randomUUID().toString(),
        name = "${tx.request.method} ${tx.request.url.substringBefore('?').takeLast(40)}",
        matcher = MockMatcher(
            method = tx.request.method,
            urlPattern = tx.request.url.substringBefore('?'),
            matchType = MockMatchType.EXACT,
        ),
        response = MockResponseSpec(
            statusCode = response.statusCode,
            headers = contentType?.let { mapOf("Content-Type" to it) }.orEmpty(),
            body = response.body.orEmpty(),
        ),
    )
}
