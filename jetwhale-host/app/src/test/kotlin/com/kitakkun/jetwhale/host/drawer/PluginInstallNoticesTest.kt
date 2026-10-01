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
    private val request = mavenRequest("network")
    private val failed = PluginInstallJob(id = "job-1", request = request, status = PluginInstallStatus.Failed("the repository is unreachable"))
    private val failureMessage = "Couldn’t install network: the repository is unreachable"

    private var jobs: ImmutableList<PluginInstallJob> by mutableStateOf(persistentListOf())
    private val opened = mutableListOf<PluginInstallJob>()
    private val retried = mutableListOf<PluginInstallRequest>()
    private val dismissed = mutableListOf<String>()
    private var dismissCalls = 0
    private var installedPluginsShown = 0
    private var installsReviewed = 0

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
        waitOutBatchWindow()

        onNodeWithText("Couldn’t install network: the repository is still unreachable").assertExists()
    }

    @Test
    fun `an install gets its notice when it finishes, not while it runs`() = runComposeUiTest {
        val running = PluginInstallJob(id = "job-1", request = request, status = PluginInstallStatus.Running(progress = null))
        showNotices(persistentListOf(running))
        onNodeWithText("network", substring = true).assertDoesNotExist()

        jobs = persistentListOf(running.copy(status = PluginInstallStatus.Succeeded))
        waitOutBatchWindow()

        onNodeWithText("network installed").assertExists()
    }

    @Test
    fun `an install dismissed from the plugin settings takes its notice with it`() = runComposeUiTest {
        showNotices(persistentListOf(failed))
        onNodeWithText(failureMessage).assertExists()

        jobs = persistentListOf()
        waitOutBatchWindow()

        onNodeWithText(failureMessage).assertDoesNotExist()
    }

    @Test
    fun `installs finishing together are announced in one notice that leads to the installed plugins`() = runComposeUiTest {
        showNotices(persistentListOf(succeeded("job-1", "network"), succeeded("job-2", "storage"), succeeded("job-3", "nav3")))

        onNodeWithText("Installed: network, storage, nav3").assertExists()
        onNodeWithText("View").performClick()
        waitForIdle()

        assertEquals(1, installedPluginsShown)
        assertEquals(listOf("job-1", "job-2", "job-3"), dismissed)
    }

    @Test
    fun `a long batch names the first few plugins and counts the rest`() = runComposeUiTest {
        showNotices(persistentListOf(succeeded("job-1", "a"), succeeded("job-2", "b"), succeeded("job-3", "c"), succeeded("job-4", "d"), succeeded("job-5", "e")))

        onNodeWithText("Installed: a, b, c, +2 more").assertExists()
    }

    @Test
    fun `failures finishing together are announced once, and reviewing them keeps them listed`() = runComposeUiTest {
        showNotices(persistentListOf(failed, failed.copy(id = "job-2", request = mavenRequest("storage"))))

        onNodeWithText("Couldn’t install: network, storage").assertExists()
        onNodeWithText("Review").performClick()
        waitForIdle()

        assertEquals(1, installsReviewed)
        assertEquals(emptyList(), dismissed)
        onNodeWithText("Couldn’t install: network, storage").assertDoesNotExist()
    }

    @Test
    fun `a batch with a failure stays until reviewed, and mentions what did install`() = runComposeUiTest {
        showNotices(persistentListOf(succeeded("job-1", "storage"), failed.copy(id = "job-2")))

        mainClock.advanceTimeBy(JwSnackbarDefaults.LONG_DURATION_MILLIS * 2)
        onNodeWithText("Installed: storage. Couldn’t install: network").assertExists()
        onNodeWithText("Review").performClick()
        waitForIdle()

        assertEquals(1, installsReviewed)
        assertEquals(listOf("job-1"), dismissed)
    }

    @Test
    fun `an install finishing while a notice is up joins that notice instead of queueing another`() = runComposeUiTest {
        showNotices(persistentListOf(failed))
        onNodeWithText(failureMessage).assertExists()

        jobs = persistentListOf(failed, succeeded("job-2", "storage"))
        waitOutBatchWindow()

        onNodeWithText(failureMessage).assertDoesNotExist()
        onNodeWithText("Installed: storage. Couldn’t install: network").assertExists()
        onNodeWithContentDescription("Dismiss").performClick()
        waitOutBatchWindow()

        assertEquals(listOf("job-1", "job-2"), dismissed)
        onNodeWithText("storage", substring = true).assertDoesNotExist()
    }

    @Test
    fun `an install finishing during the wait restarts it, and both share the notice`() = runComposeUiTest {
        mainClock.autoAdvance = false
        setNoticesContent(persistentListOf(failed))

        mainClock.advanceTimeBy(BATCH_WINDOW_MILLIS / 2)
        jobs = persistentListOf(failed, failed.copy(id = "job-2", request = mavenRequest("storage")))
        mainClock.advanceTimeBy(BATCH_WINDOW_MILLIS * 3 / 4)
        onNodeWithText(failureMessage).assertDoesNotExist()
        onNodeWithText("Couldn’t install: network, storage").assertDoesNotExist()

        mainClock.advanceTimeBy(BATCH_WINDOW_MILLIS / 2)
        onNodeWithText("Couldn’t install: network, storage").assertExists()
        onNodeWithContentDescription("Dismiss").performClick()
        mainClock.advanceTimeBy(BATCH_WINDOW_MILLIS)

        assertEquals(listOf("job-1", "job-2"), dismissed)
        assertEquals(1, dismissCalls)
    }

    @Test
    fun `closing a batch notice dismisses every install in it at once`() = runComposeUiTest {
        showNotices(persistentListOf(failed, failed.copy(id = "job-2", request = mavenRequest("storage"))))

        onNodeWithContentDescription("Dismiss").performClick()
        waitForIdle()

        assertEquals(listOf("job-1", "job-2"), dismissed)
        assertEquals(1, dismissCalls)
        assertEquals(0, installsReviewed)
    }

    private fun ComposeUiTest.showNotices(initialJobs: ImmutableList<PluginInstallJob>) {
        setNoticesContent(initialJobs)
        waitOutBatchWindow()
    }

    private fun ComposeUiTest.setNoticesContent(initialJobs: ImmutableList<PluginInstallJob>) {
        jobs = initialJobs
        setContent {
            JwTheme(darkTheme = false) {
                val hostState = remember { JwSnackbarHostState() }
                PluginInstallNotices(
                    installJobs = jobs,
                    snackbarHostState = hostState,
                    onOpen = { opened += it },
                    onRetry = { retried += it },
                    onDismiss = { jobIds ->
                        dismissCalls += 1
                        dismissed += jobIds
                    },
                    onShowInstalledPlugins = { installedPluginsShown++ },
                    onReviewInstalls = { installsReviewed++ },
                )
                JwSnackbarHost(hostState = hostState)
            }
        }
    }

    private fun ComposeUiTest.waitOutBatchWindow() {
        mainClock.advanceTimeBy(BATCH_WINDOW_MILLIS + BATCH_WINDOW_MILLIS / 2)
        waitForIdle()
    }

    private fun succeeded(id: String, artifactId: String) = PluginInstallJob(id = id, request = mavenRequest(artifactId), status = PluginInstallStatus.Succeeded)

    private fun mavenRequest(artifactId: String) = PluginInstallRequest.Maven(
        MavenCoordinates(groupId = "com.example", artifactId = artifactId, version = "1.3.0", repositoryUrl = "https://example.com/releases"),
    )
}
