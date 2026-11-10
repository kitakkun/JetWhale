package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.plugins.network.protocol.AppliedNetworkCondition
import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkConditionSummaryTest {
    @Test
    fun `a rate under one kilobyte per second reads in bytes rather than as zero`() {
        assertEquals("↓ 500 B/s • ↑ 1500 B/s", appliedRates(download = 500, upload = 1_500).summary())
    }

    @Test
    fun `a rate reads in the largest unit that shows it exactly`() {
        assertEquals("↓ 50 KB/s • ↑ 1500 KB/s", appliedRates(download = 50_000, upload = 1_500_000).summary())
        assertEquals("↓ 2 MB/s", appliedRates(download = 2_000_000, upload = null).summary())
    }

    private fun appliedRates(download: Long?, upload: Long?) = AppliedNetworkCondition(
        ruleId = "rule",
        ruleName = "Test network",
        addedLatencyMs = 0,
        downloadBytesPerSecond = download,
        uploadBytesPerSecond = upload,
        injectedFailure = null,
        offline = false,
    )
}
