package com.kitakkun.jetwhale.plugins.soil.host

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.tools.docsscreenshots.DocsScreenshot
import com.kitakkun.jetwhale.tools.docsscreenshots.DocsScreenshotRecorder
import com.kitakkun.jetwhale.tools.docsscreenshots.InMemoryPluginStorage
import com.kitakkun.jetwhale.tools.docsscreenshots.PluginSceneSurface
import com.kitakkun.jetwhale.tools.docsscreenshots.onSurface
import kotlin.test.Test

/**
 * [SoilInspectorScreen] over the preview fixtures rather than its Root: the Root binds a live
 * [SoilCacheBrowser], which needs the app on the other end, and a ticking clock.
 */
@OptIn(ExperimentalTestApi::class)
class SoilInspectorDocsScreenshots {
    private val recorder = DocsScreenshotRecorder.fromImagesDirectorySystemProperty()

    @Test
    fun `a query explained beside the cache with the timeline below`() = recorder.record(
        DocsScreenshot(page = "soil-inspector", name = "cache", surfaceSize = SURFACE_SIZE, density = 1.6f, displayWidthCssPx = 688),
    ) { darkTheme ->
        setContent {
            PluginSceneSurface(darkTheme = darkTheme, storage = InMemoryPluginStorage(emptyMap())) {
                FixtureSoilInspectorScreen(selectedEntry = FixtureUserQuery, selectedValue = FixtureUserValue, selectedEventSequence = null, isFollowingEvents = true)
            }
        }
        onSurface()
    }

    @Test
    fun `a mutation with what followed its last run`() = recorder.record(
        DocsScreenshot(page = "soil-inspector", name = "mutation", surfaceSize = SURFACE_SIZE, density = 1.6f, displayWidthCssPx = 688),
    ) { darkTheme ->
        setContent {
            PluginSceneSurface(darkTheme = darkTheme, storage = InMemoryPluginStorage(emptyMap())) {
                FixtureSoilInspectorScreen(selectedEntry = FixtureRenameMutation, selectedValue = FixtureRenamedUserValue, selectedEventSequence = 2, isFollowingEvents = false)
            }
        }
        onSurface()
    }

    private companion object {
        val SURFACE_SIZE = DpSize(860.dp, 760.dp)
    }
}
