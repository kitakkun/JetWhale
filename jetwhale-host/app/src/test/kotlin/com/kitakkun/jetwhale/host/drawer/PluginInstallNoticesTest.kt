package com.kitakkun.jetwhale.host.drawer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.host.model.MavenCoordinates
import com.kitakkun.jetwhale.host.model.PluginInstallJob
import com.kitakkun.jetwhale.host.model.PluginInstallRequest
import com.kitakkun.jetwhale.host.model.PluginInstallStatus
import com.kitakkun.jetwhale.host.ui.JwSnackbarDefaults
import com.kitakkun.jetwhale.host.ui.JwSnackbarHost
import com.kitakkun.jetwhale.host.ui.JwSnackbarHostState
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PluginInstallNoticesTest {
    private val request = PluginInstallRequest.Maven(
        MavenCoordinates(groupId = "com.example", artifactId = "network", version = "1.3.0", repositoryUrl = "https://example.com/releases"),
    )
    private val failed = PluginInstallJob(id = "job-1", request = request, status = PluginInstallStatus.Failed("the repository is unreachable"))
    private val failureMessage = "Couldn’t install network: the repository is unreachable"

    private var jobs: ImmutableList<PluginInstallJob> by mutableStateOf(persistentListOf())
    private val opened = mutableListOf<PluginInstallJob>()
    private val retried = mutableListOf<PluginInstallRequest>()
    private val dismissed = mutableListOf<String>()

    @Test
    fun `a finished install offers to open the plugin, and leaves the list once opened`() = runComposeUiTest {
        val job = PluginInstallJob(id = "job-1", request = request, status = PluginInstallStatus.Succeeded)
        showNotices(persistentListOf(job))

        onNodeWithText("network installed").assertExists()
        onNodeWithText("Open").performClick()
        waitForIdle()

        assertEquals(listOf(job), opened)
        assertEquals(listOf("job-1"), dismissed)
    }

    @Test
    fun `a failed install stays on screen until the user closes it`() = runComposeUiTest {
        showNotices(persistentListOf(failed))

        mainClock.advanceTimeBy(JwSnackbarDefaults.LONG_DURATION_MILLIS * 2)
        onNodeWithText(failureMessage).assertExists()
        assertEquals(emptyList(), dismissed)

        onNodeWithContentDescription("Dismiss").performClick()
        waitForIdle()

        assertEquals(listOf("job-1"), dismissed)
        assertEquals(emptyList(), retried)
    }

    @Test
    fun `retrying a failed install starts the same install again`() = runComposeUiTest {
        showNotices(persistentListOf(failed))

        onNodeWithText("Retry").performClick()
        waitForIdle()

        assertEquals(listOf<PluginInstallRequest>(request), retried)
        assertEquals(emptyList(), dismissed)
    }

    @Test
    fun `a retry that fails again gets a notice of its own`() = runComposeUiTest {
        showNotices(persistentListOf(failed))
        onNodeWithText("Retry").performClick()
        waitForIdle()

        jobs = persistentListOf(failed.copy(id = "job-2", status = PluginInstallStatus.Failed("the repository is still unreachable")))
        waitForIdle()

        onNodeWithText("Couldn’t install network: the repository is still unreachable").assertExists()
    }

    @Test
    fun `an install gets its notice when it finishes, not while it runs`() = runComposeUiTest {
        val running = PluginInstallJob(id = "job-1", request = request, status = PluginInstallStatus.Running(progress = null))
        showNotices(persistentListOf(running))
        onNodeWithText("network", substring = true).assertDoesNotExist()

        jobs = persistentListOf(running.copy(status = PluginInstallStatus.Succeeded))
        waitForIdle()

        onNodeWithText("network installed").assertExists()
    }

    @Test
    fun `an install dismissed from the plugin settings takes its notice with it`() = runComposeUiTest {
        showNotices(persistentListOf(failed))
        onNodeWithText(failureMessage).assertExists()

        jobs = persistentListOf()
        waitForIdle()

        onNodeWithText(failureMessage).assertDoesNotExist()
    }

    private fun ComposeUiTest.showNotices(initialJobs: ImmutableList<PluginInstallJob>) {
        jobs = initialJobs
        setContent {
            JwTheme(darkTheme = false) {
                val hostState = remember { JwSnackbarHostState() }
                PluginInstallNotices(
                    installJobs = jobs,
                    snackbarHostState = hostState,
                    onOpen = { opened += it },
                    onRetry = { retried += it },
                    onDismiss = { dismissed += it },
                )
                JwSnackbarHost(hostState = hostState)
            }
        }
    }
}
