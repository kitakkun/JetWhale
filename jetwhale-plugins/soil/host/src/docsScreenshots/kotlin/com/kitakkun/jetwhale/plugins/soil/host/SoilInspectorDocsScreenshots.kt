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
 * [SoilInspectorScreen] rather than its Root: the Root binds a live [SoilCacheBrowser], which needs
 * the app on the other end, and a ticking clock.
 */
@OptIn(ExperimentalTestApi::class)
class SoilInspectorDocsScreenshots {
    private val recorder = DocsScreenshotRecorder.fromImagesDirectorySystemProperty()

    @Test
    fun `the cache grouped by kind beside a selected query with its value`() = recorder.record(
        DocsScreenshot(page = "soil-inspector", name = "cache", surfaceSize = DpSize(860.dp, 720.dp), density = 1.6f, displayWidthCssPx = 688),
    ) { darkTheme ->
        setContent {
            PluginSceneSurface(darkTheme = darkTheme, storage = InMemoryPluginStorage(emptyMap())) {
                SoilInspectorScreen(
                    coverage = FixtureCoverage,
                    listedEntries = FixtureEntries,
                    selectedEntry = FixtureUserQuery,
                    selectedValue = FixtureUserValue,
                    status = null,
                    searchQuery = "",
                    agentNowEpochSeconds = FIXTURE_NOW,
                    actions = NoSoilInspectorActions,
                    onSearchQueryChange = {},
                )
            }
        }
        onSurface()
    }
}
