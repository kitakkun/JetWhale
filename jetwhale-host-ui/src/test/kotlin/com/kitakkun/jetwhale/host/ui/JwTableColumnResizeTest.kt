package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.dragAndDrop
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
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

        assertClose(140.dp, state.widths.getValue(NAME_COLUMN))
    }

    @Test
    fun `a column is not dragged narrower than its minimum`() = runTable(
        columns = listOf(textColumn("Name", JwColumnWidth.Fixed(100.dp)), textColumn("Value", JwColumnWidth.Weight(1f))),
    ) { state ->
        drag("Name", by = -500f)

        assertEquals(JwTableDefaults.minColumnWidth, state.widths.getValue(NAME_COLUMN))
    }

    @Test
    fun `a resized weight column becomes fixed and the other weight column takes the rest`() = runTable(
        columns = listOf(textColumn("Name", JwColumnWidth.Weight(1f)), textColumn("Value", JwColumnWidth.Weight(1f))),
    ) { state ->
        val valueBefore = state.laidOutWidths.getValue(VALUE_COLUMN)

        drag("Name", by = 50f)

        assertClose(state.laidOutWidths.getValue(NAME_COLUMN), state.widths.getValue(NAME_COLUMN))
        assertNull(state.widths[VALUE_COLUMN], "the other column still shares the width by weight")
        assertClose(valueBefore - 50.dp, state.laidOutWidths.getValue(VALUE_COLUMN))
    }

    @Test
    fun `a drag stops where the weight columns would go below their minimum`() = runTable(
        columns = listOf(textColumn("Name", JwColumnWidth.Weight(1f)), textColumn("Value", JwColumnWidth.Weight(1f))),
    ) { state ->
        drag("Name", by = 1_000f)

        assertClose(JwTableDefaults.minColumnWidth, state.laidOutWidths.getValue(VALUE_COLUMN))
        assertClose(state.laidOutWidths.getValue(NAME_COLUMN), state.widths.getValue(NAME_COLUMN), "nothing past the edge is stored, so dragging back moves at once")
    }

    @Test
    fun `a drag stops where the weight column with the smallest weight would go below its minimum`() = runTable(
        columns = listOf(
            textColumn("Key", JwColumnWidth.Weight(1f)),
            textColumn("Type", JwColumnWidth.Fixed(80.dp)),
            textColumn("Value", JwColumnWidth.Weight(2f)),
        ),
    ) { state ->
        drag("Type", by = 1_000f)

        assertClose(JwTableDefaults.minColumnWidth, state.laidOutWidths.getValue(JwTableColumnKey(0, "Key")))
        assertClose(JwTableDefaults.minColumnWidth * 2, state.laidOutWidths.getValue(JwTableColumnKey(2, "Value")))
    }

    @Test
    fun `the resize handle of a column that scrolls sideways sits at the column's trailing edge`() = runTable(
        columns = listOf(
            JwTableColumn.text<String>(header = "Name", width = JwColumnWidth.Fixed(100.dp), overflow = JwColumnOverflow.Scroll) { it },
            textColumn("Value", JwColumnWidth.Weight(1f)),
        ),
    ) { _ ->
        val columnStart = onNodeWithText("Name").getBoundsInRoot().left
        val handleEnd = onNodeWithContentDescription("Resize Name").getBoundsInRoot().right

        assertClose(100.dp, handleEnd - columnStart)
    }

    @Test
    fun `a dragged width gives way when the table gets narrower`() = runComposeUiTest {
        val state = JwTableColumnState(mapOf(NAME_COLUMN to 300.dp))
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

        assertClose(JwTableDefaults.minColumnWidth, state.laidOutWidths.getValue(VALUE_COLUMN))
        assertEquals(300.dp, state.widths.getValue(NAME_COLUMN), "the user's width is kept for when the table is wide again")
    }

    @Test
    fun `two dragged widths share a narrower table in column order without leaving part of the row empty`() = runComposeUiTest {
        val state = JwTableColumnState(mapOf(NAME_COLUMN to 300.dp, VALUE_COLUMN to 300.dp))
        setContent {
            JwTheme(darkTheme = false) {
                Box(Modifier.requiredSize(width = TABLE_WIDTH, height = 300.dp)) {
                    JwTable(
                        items = listOf("alpha"),
                        columns = listOf(textColumn("Name", JwColumnWidth.Weight(1f)), textColumn("Value", JwColumnWidth.Weight(1f))),
                        columnState = state,
                    )
                }
            }
        }
        waitForIdle()

        val rowPadding = JwSpacing.medium * 2
        val gapBetweenColumns = JwSpacing.medium
        val widthForColumns = TABLE_WIDTH - rowPadding - gapBetweenColumns
        assertClose(300.dp, state.laidOutWidths.getValue(NAME_COLUMN))
        assertClose(widthForColumns - 300.dp, state.laidOutWidths.getValue(VALUE_COLUMN))
        assertEquals(mapOf(NAME_COLUMN to 300.dp, VALUE_COLUMN to 300.dp), state.widths, "the user's widths are kept for when the table is wide again")
    }

    @OptIn(InternalComposeUiApi::class)
    @Test
    fun `a fit finishes in a scene whose effects start before layout, as a plugin's does`() {
        val state = JwTableColumnState(emptyMap())
        val scene = CanvasLayersComposeScene(size = IntSize(SCENE_WIDTH_PX, SCENE_HEIGHT_PX), coroutineContext = Dispatchers.Unconfined)
        try {
            scene.setContent {
                JwTheme(darkTheme = false) {
                    JwTable(
                        items = listOf("short", "a considerably longer name than the column"),
                        columns = listOf(textColumn("Name", JwColumnWidth.Fixed(60.dp)), textColumn("Value", JwColumnWidth.Weight(1f))),
                        columnState = state,
                    )
                }
            }
            val canvas = Canvas(ImageBitmap(SCENE_WIDTH_PX, SCENE_HEIGHT_PX))
            scene.render(canvas, 0L)

            state.fitting = NAME_COLUMN
            repeat(3) { frame -> scene.render(canvas, (frame + 1) * FRAME_NANOS) }

            val fitted = state.widths[NAME_COLUMN]
            assertTrue(fitted != null && fitted > 150.dp, "the column grew to the long name, now $fitted")
        } finally {
            scene.close()
        }
    }

    @Test
    fun `double-clicking a header edge fits the column to its widest content`() = runTable(
        columns = listOf(textColumn("Name", JwColumnWidth.Fixed(60.dp)), textColumn("Value", JwColumnWidth.Weight(1f))),
        names = listOf("short", "a considerably longer name than the column"),
    ) { state ->
        onNodeWithContentDescription("Resize Name").performMouseInput { doubleClick(center) }
        waitForIdle()

        val fitted = state.widths.getValue(NAME_COLUMN)
        assertTrue(fitted > 150.dp, "the column grew to the long name, now $fitted")
    }

    @Test
    fun `a double-click fits the column in the column state the table was given last`() = runComposeUiTest {
        val first = JwTableColumnState(emptyMap())
        val second = JwTableColumnState(emptyMap())
        var columnState by mutableStateOf(first)
        setContent {
            JwTheme(darkTheme = false) {
                Box(Modifier.requiredSize(width = TABLE_WIDTH, height = 300.dp)) {
                    JwTable(
                        items = listOf("short", "a considerably longer name than the column"),
                        columns = listOf(textColumn("Name", JwColumnWidth.Fixed(60.dp)), textColumn("Value", JwColumnWidth.Weight(1f))),
                        columnState = columnState,
                    )
                }
            }
        }
        val handle = onNodeWithContentDescription("Resize Name")
        handle.performMouseInput { moveTo(center) }
        waitForIdle()

        columnState = second
        waitForIdle()
        handle.performMouseInput { doubleClick(center) }
        waitForIdle()

        val fitted = second.widths[NAME_COLUMN]
        assertTrue(fitted != null && fitted > 150.dp, "the column grew to the long name, now $fitted")
        assertEquals(emptyMap(), first.widths)
    }

    @Test
    fun `a fit leaves out cells that cannot report their width and fits the others`() = runTable(
        columns = listOf(
            JwTableColumn<String>(header = "Name", width = JwColumnWidth.Fixed(60.dp)) { name ->
                when (name) {
                    BOX_WITH_CONSTRAINTS_ROW -> BoxWithConstraints { JwText(name) }
                    LAZY_LIST_ROW -> LazyRow { item { JwText(name) } }
                    else -> JwTableCellText(name)
                }
            },
            textColumn("Value", JwColumnWidth.Weight(1f)),
        ),
        names = listOf(BOX_WITH_CONSTRAINTS_ROW, LAZY_LIST_ROW, "a considerably longer name than the column"),
    ) { state ->
        onNodeWithContentDescription("Resize Name").performMouseInput { doubleClick(center) }
        waitForIdle()

        val fitted = state.widths.getValue(NAME_COLUMN)
        assertTrue(fitted > 150.dp, "the column grew to the long name, now $fitted")
    }

    @Test
    fun `columns that share a header resize independently`() = runTable(
        columns = listOf(textColumn("", JwColumnWidth.Fixed(60.dp)), textColumn("", JwColumnWidth.Fixed(60.dp)), textColumn("Value", JwColumnWidth.Weight(1f))),
    ) { state ->
        drag("column 1", by = 40f)

        assertClose(100.dp, state.widths.getValue(JwTableColumnKey(0, "")))
        assertNull(state.widths[JwTableColumnKey(1, "")])
        assertClose(60.dp, state.laidOutWidths.getValue(JwTableColumnKey(1, "")))
    }

    @Test
    fun `a resize handle is named by its column's position when the header does not tell the column apart`() = runTable(
        columns = listOf(textColumn("", JwColumnWidth.Fixed(60.dp)), textColumn("Size", JwColumnWidth.Fixed(60.dp)), textColumn("Size", JwColumnWidth.Fixed(60.dp)), textColumn("Value", JwColumnWidth.Weight(1f))),
    ) { _ ->
        listOf("Resize column 1", "Resize column 2", "Resize column 3", "Resize Value").forEach { name ->
            onNodeWithContentDescription(name).assertExists()
        }
    }

    @Test
    fun `the saver restores the dragged widths`() {
        val state = JwTableColumnState(mapOf(NAME_COLUMN to 140.dp, VALUE_COLUMN to 80.dp))

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

private val NAME_COLUMN = JwTableColumnKey(0, "Name")

private val VALUE_COLUMN = JwTableColumnKey(1, "Value")

private const val BOX_WITH_CONSTRAINTS_ROW = "box"

private const val LAZY_LIST_ROW = "list"

/** The scene runs at density 1, so these are also its size in dp. */
private const val SCENE_WIDTH_PX = 400

private const val SCENE_HEIGHT_PX = 300

private const val FRAME_NANOS = 16_000_000L
