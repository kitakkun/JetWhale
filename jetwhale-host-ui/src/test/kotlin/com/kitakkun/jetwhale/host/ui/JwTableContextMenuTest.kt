package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class JwTableContextMenuTest {
    @Test
    fun `right-clicking a row offers the context menu items of its own item`() = runComposeUiTest {
        setTable(onClick = {})

        onNodeWithText("second").performMouseInput { rightClick() }

        onNodeWithText("Open second").assertExists()
        onNodeWithText("Open first").assertDoesNotExist()
    }

    @Test
    fun `a read-only row offers its context menu too`() = runComposeUiTest {
        setTable(onClick = null)

        onNodeWithText("first").performMouseInput { rightClick() }

        onNodeWithText("Open first").assertExists()
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.setTable(onClick: ((String) -> Unit)?) {
    setContent {
        JwTheme(darkTheme = false) {
            JwTable(
                items = listOf("first", "second"),
                columns = listOf(JwTableColumn.text(header = "Name", width = JwColumnWidth.Weight(1f)) { it }),
                onClick = onClick,
                contextMenuItems = { item -> listOf(ContextMenuItem("Open $item") {}) },
            )
        }
    }
}
