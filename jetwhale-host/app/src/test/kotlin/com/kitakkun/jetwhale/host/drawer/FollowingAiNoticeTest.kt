package com.kitakkun.jetwhale.host.drawer

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.host.ui.JwSnackbarDefaults
import com.kitakkun.jetwhale.host.ui.JwSnackbarDuration
import com.kitakkun.jetwhale.host.ui.JwSnackbarHost
import com.kitakkun.jetwhale.host.ui.JwSnackbarHostState
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class FollowingAiNoticeTest {
    private val snackbarHostState = JwSnackbarHostState()
    private val shownMessages = mutableListOf<String>()
    private lateinit var scope: CoroutineScope
    private val followingAiNotice by lazy { FollowingAiNotice(snackbarHostState, scope) }

    @Test
    fun `back-to-back follows show only the latest plugin's name`() = runComposeUiTest {
        mainClock.autoAdvance = false
        setSnackbarHostContent()
        followingAiNotice.show("Network")
        awaitShown("Following the AI: Network")

        followingAiNotice.show("Storage")
        followingAiNotice.show("Mirror")
        awaitShown("Following the AI: Mirror")
        mainClock.advanceTimeBy(JwSnackbarDefaults.LONG_DURATION_MILLIS)

        assertEquals(listOf("Following the AI: Network", "Following the AI: Mirror"), shownMessages)
    }

    @Test
    fun `the notice leaves after two seconds`() = runComposeUiTest {
        mainClock.autoAdvance = false
        setSnackbarHostContent()
        followingAiNotice.show("Network")
        awaitShown("Following the AI: Network")

        mainClock.advanceTimeBy(FOLLOWING_AI_NOTICE_DURATION_MILLIS - 500)
        onNodeWithText("Following the AI: Network").assertExists()

        mainClock.advanceTimeBy(1_500)
        onNodeWithText("Following the AI: Network").assertDoesNotExist()
    }

    @Test
    fun `a message waiting behind the notice still shows, ahead of the follows after it`() = runComposeUiTest {
        mainClock.autoAdvance = false
        setSnackbarHostContent()
        followingAiNotice.show("Network")
        awaitShown("Following the AI: Network")
        scope.launch { snackbarHostState.showSnackbar("Demo app connected", duration = JwSnackbarDuration.Short) }
        mainClock.advanceTimeByFrame()

        followingAiNotice.show("Storage")
        awaitShown("Demo app connected")
        followingAiNotice.show("Mirror")
        mainClock.advanceTimeBy(1_000)
        onNodeWithText("Demo app connected").assertExists()

        mainClock.advanceTimeBy(JwSnackbarDefaults.SHORT_DURATION_MILLIS)
        awaitShown("Following the AI: Mirror")
        assertEquals(listOf("Following the AI: Network", "Demo app connected", "Following the AI: Mirror"), shownMessages)
    }

    private fun ComposeUiTest.setSnackbarHostContent() {
        setContent {
            scope = rememberCoroutineScope()
            LaunchedEffect(Unit) {
                snapshotFlow { snackbarHostState.currentSnackbarData?.message }.filterNotNull().collect { shownMessages += it }
            }
            JwTheme(darkTheme = false) {
                JwSnackbarHost(hostState = snackbarHostState)
            }
        }
    }

    private fun ComposeUiTest.awaitShown(message: String) {
        waitUntil {
            mainClock.advanceTimeByFrame()
            onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
