package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class JwListRowKeysTest {
    private val rows = List(60) { "row $it" }

    @Test
    fun `down and up move focus and the selection to the neighboring row`() = runComposeUiTest {
        var selected by mutableStateOf<String?>(null)
        setContent { Table(selected = selected, onSelect = { selected = it }) }

        onNodeWithText("row 3").performClick()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionUp)

        assertEquals("row 4", selected)
        onNodeWithText("row 4").assertIsFocused()
    }

    @Test
    fun `walking past the bottom edge scrolls the next row into view and walking back scrolls up`() = runComposeUiTest {
        var selected by mutableStateOf<String?>(null)
        setContent { Table(selected = selected, onSelect = { selected = it }) }

        onNodeWithText("row 0").performClick()
        repeat(30) { press(Key.DirectionDown) }

        assertEquals("row 30", selected)
        onNodeWithText("row 30").assertIsDisplayed()

        repeat(30) { press(Key.DirectionUp) }

        assertEquals("row 0", selected)
        onNodeWithText("row 0").assertIsDisplayed()
    }

    @Test
    fun `a click selects a row once`() = runComposeUiTest {
        val selections = mutableListOf<String>()
        setContent { Table(selected = selections.lastOrNull(), onSelect = selections::add) }

        onNodeWithText("row 2").performClick()

        assertEquals(listOf("row 2"), selections)
    }

    @Test
    fun `arrow keys typed into a text field inside a row leave the selection alone`() = runComposeUiTest {
        var selected by mutableStateOf<String?>("row 1")
        var note by mutableStateOf("")
        setContent {
            JwTheme(darkTheme = false) {
                JwTable(
                    items = rows.take(5),
                    columns = listOf(
                        JwTableColumn(header = "Note", width = JwColumnWidth.Weight(1f)) { row ->
                            if (row == "row 1") JwTextField(value = note, onValueChange = { note = it }, placeholder = "Type a note") else JwText(row)
                        },
                    ),
                    key = { it },
                    isSelected = { it == selected },
                    onClick = { selected = it },
                    modifier = Modifier.height(200.dp),
                )
            }
        }

        onNodeWithText("Type a note").performClick()
        press(Key.DirectionDown)
        press(Key.DirectionUp)

        assertEquals("row 1", selected)
        onNodeWithText("Type a note").assertIsFocused()
    }

    @Test
    fun `right expands a collapsed tree row and left collapses an expanded one`() = runComposeUiTest {
        val collapsed = mutableStateListOf("parent")
        var selected by mutableStateOf<String?>(null)
        setContent { Tree(collapsed = collapsed, selected = selected, onSelect = { selected = it }) }

        onNodeWithText("parent").performClick()
        press(Key.DirectionRight)
        assertEquals(emptyList(), collapsed.toList())

        press(Key.DirectionRight)
        assertEquals(emptyList(), collapsed.toList(), "right on an expanded row changes nothing")
        assertEquals("parent", selected)

        press(Key.DirectionLeft)
        assertEquals(listOf("parent"), collapsed.toList())
    }

    @Test
    fun `down from a tree row lands on the next row, not its chevron`() = runComposeUiTest {
        var selected by mutableStateOf<String?>(null)
        setContent { Tree(collapsed = mutableStateListOf(), selected = selected, onSelect = { selected = it }) }

        onNodeWithText("parent").performClick()
        press(Key.DirectionDown)

        assertEquals("child", selected)
        onNodeWithText("child").assertIsFocused()
    }

    @Test
    fun `up on the first row and down on the last stay in the list instead of reaching the controls around it`() = runComposeUiTest {
        var selected by mutableStateOf<String?>(null)
        var filter by mutableStateOf("")
        setContent {
            JwTheme(darkTheme = false) {
                Column {
                    JwTextField(value = filter, onValueChange = { filter = it }, placeholder = "Filter")
                    JwTable(
                        items = rows.take(3),
                        columns = listOf(JwTableColumn.text(header = "Name", width = JwColumnWidth.Weight(1f)) { it }),
                        key = { it },
                        isSelected = { it == selected },
                        onClick = { selected = it },
                        modifier = Modifier.height(200.dp),
                    )
                    JwButton(text = "Below", onClick = {})
                }
            }
        }

        onNodeWithText("row 0").performClick()
        press(Key.DirectionUp)
        onNodeWithText("row 0").assertIsFocused()

        onNodeWithText("row 2").performClick()
        press(Key.DirectionDown)
        onNodeWithText("row 2").assertIsFocused()
        assertEquals("row 2", selected)
    }

    private fun ComposeUiTest.press(key: Key) {
        onNode(isFocused()).performKeyInput { pressKey(key) }
        mainClock.advanceTimeByFrame()
        waitForIdle()
    }

    @Composable
    private fun Table(selected: String?, onSelect: (String) -> Unit) {
        JwTheme(darkTheme = false) {
            JwTable(
                items = rows,
                columns = listOf(JwTableColumn.text(header = "Name", width = JwColumnWidth.Weight(1f)) { it }),
                key = { it },
                isSelected = { it == selected },
                onClick = onSelect,
                modifier = Modifier.height(200.dp),
            )
        }
    }

    /** parent > child, then sibling. */
    @Composable
    private fun Tree(collapsed: MutableList<String>, selected: String?, onSelect: (String) -> Unit) {
        val visible = if ("parent" in collapsed) listOf("parent" to 0, "sibling" to 0) else listOf("parent" to 0, "child" to 1, "sibling" to 0)
        JwTheme(darkTheme = false) {
            LazyColumn {
                items(visible, key = { it.first }) { (name, depth) ->
                    JwTreeRow(
                        text = name,
                        depth = depth,
                        expandable = name == "parent",
                        expanded = name !in collapsed,
                        selected = name == selected,
                        onClick = { onSelect(name) },
                        onToggleExpanded = { if (!collapsed.remove(name)) collapsed += name },
                    )
                }
            }
        }
    }
}
