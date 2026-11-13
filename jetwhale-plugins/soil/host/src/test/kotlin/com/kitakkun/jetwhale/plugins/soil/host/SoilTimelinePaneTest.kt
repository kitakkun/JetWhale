package com.kitakkun.jetwhale.plugins.soil.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEvent
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEventKind
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SoilTimelinePaneTest {
    private val profile = queryEntry(handle = "query-1", namespace = "users/profile")
    private val clockSubscription = subscriptionEntry(handle = "subscription-2", namespace = "clock/ticks")

    @Test
    fun `following keeps the newest event in view as the timeline grows past the pane`() = runComposeUiTest {
        val fetches = (1L..60L).map { eventOf(it, profile, if (it % 2 == 0L) SoilEventKind.FETCH_SUCCEEDED else SoilEventKind.FETCH_STARTED, atEpochMillis = it * 5_000) }
        setContent { Timeline(fetches, isFollowing = true, onSelectEvent = {}) }

        onNode(hasText("00:05:00.000", substring = true)).assertExists()
        onNode(hasText("00:00:05.000", substring = true)).assertDoesNotExist()
    }

    @Test
    fun `repeats in a row share one line with their count and select the latest`() = runComposeUiTest {
        val selectedEventSequences = mutableListOf<Long>()
        val ticks = (1L..3L).map { eventOf(it, clockSubscription, SoilEventKind.SUBSCRIPTION_DATA_RECEIVED, atEpochMillis = 1_000 + it * 100) }
        setContent { Timeline(ticks, isFollowing = false, onSelectEvent = { selectedEventSequences += it }) }

        onNode(hasText("×3")).performClick()

        assertEquals(listOf(3L), selectedEventSequences)
    }

    @Test
    fun `repeats more than a burst apart keep lines of their own with the pause between them`() = runComposeUiTest {
        val ticks = (1L..2L).map { eventOf(it, clockSubscription, SoilEventKind.SUBSCRIPTION_DATA_RECEIVED, atEpochMillis = it * 5_000) }
        setContent { Timeline(ticks, isFollowing = false, onSelectEvent = {}) }

        onNode(hasText("5.0s later")).assertExists()
        onNode(hasText("×2", substring = true)).assertDoesNotExist()
    }

    @Composable
    private fun Timeline(events: List<SoilEvent>, isFollowing: Boolean, onSelectEvent: (Long) -> Unit) {
        JwTheme(darkTheme = false) {
            SoilTimelinePane(
                events = events,
                selectedEventSequence = null,
                selectedHandle = null,
                settings = SoilTimelineSettings.Initial,
                isFollowing = isFollowing,
                timeOfDayFormatter = TimeOfDayFormatter(ZoneOffset.UTC),
                onSettingsChange = {},
                onFollowingChange = {},
                onSelectEvent = onSelectEvent,
                onClear = {},
            )
        }
    }
}
