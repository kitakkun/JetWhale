package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.dragAndDrop
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class JwTableColumnResizeTest {

    @Test
    fun `dragging a header edge widens the column by the drag`() = runTable(
        columns = listOf(textColumn("Name", JwColumnWidth.Fixed(100.dp)), textColumn("Value", JwColumnWidth.Weight(1f))),
    ) { state ->
        drag("Name", by = 40f)

        assertClose(140.dp, state.widths.getValue("Name"))
    }

    @Test
    fun `a column is not dragged narrower than its minimum`() = runTable(
        columns = listOf(textColumn("Name", JwColumnWidth.Fixed(100.dp)), textColumn("Value", JwColumnWidth.Weight(1f))),
    ) { state ->
        drag("Name", by = -500f)

        assertEquals(JwTableDefaults.minColumnWidth, state.widths.getValue("Name"))
    }

    @Test
    fun `a resized weight column becomes fixed and the other weight column takes the rest`() = runTable(
        columns = listOf(textColumn("Name", JwColumnWidth.Weight(1f)), textColumn("Value", JwColumnWidth.Weight(1f))),
    ) { state ->
        val valueBefore = state.laidOutWidths.getValue("Value")

        drag("Name", by = 50f)

        assertClose(state.laidOutWidths.getValue("Name"), state.widths.getValue("Name"))
        assertNull(state.widths["Value"], "the other column still shares the width by weight")
        assertClose(valueBefore - 50.dp, state.laidOutWidths.getValue("Value"))
    }

    @Test
    fun `a drag stops where the weight columns would go below their minimum`() = runTable(
        columns = listOf(textColumn("Name", JwColumnWidth.Weight(1f)), textColumn("Value", JwColumnWidth.Weight(1f))),
    ) { state ->
        drag("Name", by = 1_000f)

        assertClose(JwTableDefaults.minColumnWidth, state.laidOutWidths.getValue("Value"))
        assertClose(state.laidOutWidths.getValue("Name"), state.widths.getValue("Name"), "nothing past the edge is stored, so dragging back moves at once")
    }

    @Test
    fun `a dragged width gives way when the table gets narrower`() = runComposeUiTest {
        val state = JwTableColumnState(mapOf("Name" to 300.dp))
        var tableWidth by mutableStateOf(TABLE_WIDTH)
        setContent {
            JwTheme(darkTheme = false) {
                Box(Modifier.requiredSize(width = tableWidth, height = 300.dp)) {
                    JwTable(
                        items = listOf("alpha"),
                        columns = listOf(textColumn("Name", JwColumnWidth.Weight(1f)), textColumn("Value", JwColumnWidth.Weight(1f))),
                        columnState = state,
                    )
                }
            }
        }
        waitForIdle()

        tableWidth = 200.dp
        waitForIdle()

        assertClose(JwTableDefaults.minColumnWidth, state.laidOutWidths.getValue("Value"))
        assertEquals(300.dp, state.widths.getValue("Name"), "the user's width is kept for when the table is wide again")
    }

    @Test
    fun `double-clicking a header edge fits the column to its widest content`() = runTable(
        columns = listOf(textColumn("Name", JwColumnWidth.Fixed(60.dp)), textColumn("Value", JwColumnWidth.Weight(1f))),
        names = listOf("short", "a considerably longer name than the column"),
    ) { state ->
        onNodeWithContentDescription("Resize Name").performMouseInput { doubleClick(center) }
        waitForIdle()

        val fitted = state.widths.getValue("Name")
        assertTrue(fitted > 150.dp, "the column grew to the long name, now $fitted")
    }

    @Test
    fun `the saver restores the dragged widths`() {
        val state = JwTableColumnState(mapOf("Name" to 140.dp, "Value" to 80.dp))

        val saved = with(JwTableColumnState.Saver) { SaverScope { true }.save(state) }
        val restored = JwTableColumnState.Saver.restore(checkNotNull(saved))

        assertEquals(state.widths, checkNotNull(restored).widths)
    }

    private fun ComposeUiTest.drag(header: String, by: Float) {
        val px = with(density) { by.dp.toPx() }
        onNodeWithContentDescription("Resize $header").performMouseInput {
            dragAndDrop(start = center, end = center + Offset(px, 0f))
        }
        waitForIdle()
    }

    private fun runTable(
        columns: List<JwTableColumn<String>>,
        names: List<String> = listOf("alpha", "beta"),
        block: ComposeUiTest.(JwTableColumnState) -> Unit,
    ) = runComposeUiTest {
        val state = JwTableColumnState(emptyMap())
        setContent {
            JwTheme(darkTheme = false) {
                Box(Modifier.requiredSize(width = TABLE_WIDTH, height = 300.dp)) {
                    JwTable(items = names, columns = columns, columnState = state)
                }
            }
        }
        waitForIdle()
        block(state)
    }

    private fun textColumn(header: String, width: JwColumnWidth) = JwTableColumn.text<String>(header = header, width = width, text = { it })

    private fun assertClose(expected: Dp, actual: Dp, message: String? = null) {
        assertTrue(actual in (expected - 2.dp)..(expected + 2.dp), "${message?.let { "$it: " }.orEmpty()}expected about $expected, was $actual")
    }
}

private val TABLE_WIDTH = 400.dp
