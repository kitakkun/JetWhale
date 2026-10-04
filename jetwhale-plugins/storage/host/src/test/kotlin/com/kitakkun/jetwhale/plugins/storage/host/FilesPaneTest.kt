package com.kitakkun.jetwhale.plugins.storage.host

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMultiModalInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.storage.protocol.FileRootInfo
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class FilesPaneTest {
    @Test
    fun `an arrow-key move onto a directory selects it, even after an Alt-click toggled its subtree`() = runComposeUiTest {
        val actions = RecordingActions()
        setContent {
            JwTheme(darkTheme = false) {
                FilesPane(
                    treeRows = listOf(rootRow("cache"), rootRow("files")),
                    fileRoots = listOf(FileRootInfo(name = "cache", absolutePath = "/data/cache"), FileRootInfo(name = "files", absolutePath = "/data/files")),
                    selectedRow = null,
                    loadedFile = null,
                    directoryMeasurement = null,
                    fileSha256 = null,
                    actions = actions,
                )
            }
        }

        onNodeWithText("cache").performMultiModalInput {
            key { keyDown(Key.AltLeft) }
            mouse { click() }
            key { keyUp(Key.AltLeft) }
        }
        assertEquals(listOf("toggleSubtree cache"), actions.calls)

        onNodeWithText("files").performClick()
        onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }
        waitForIdle()

        assertEquals(listOf("toggleSubtree cache", "select files", "select cache"), actions.calls)
    }

    private fun rootRow(name: String) = FileTreeRow(location = FileLocation(rootName = name, path = emptyList()), depth = 0, entry = null, expanded = false)

    private class RecordingActions : StorageInspectorActions {
        val calls = mutableListOf<String>()

        override fun select(row: FileTreeRow) {
            calls += "select ${row.location.name}"
        }

        override fun toggleDirectory(location: FileLocation) {
            calls += "toggleDirectory ${location.name}"
        }

        override fun toggleSubtree(location: FileLocation) {
            calls += "toggleSubtree ${location.name}"
        }

        override fun refresh() = Unit

        override fun delete(location: FileLocation) = Unit

        override fun saveFile(location: FileLocation, target: File) = Unit

        override fun measureDirectory(location: FileLocation) = Unit

        override fun computeSha256(location: FileLocation) = Unit

        override fun selectStore(storeName: String) = Unit

        override fun removeKey(storeName: String, key: String) = Unit
    }
}
