package com.kitakkun.jetwhale.plugins.semantics.host

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeRoot
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeSnapshot
import com.kitakkun.jetwhale.plugins.semantics.protocol.UiNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttribute
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeValue
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewNode
import com.kitakkun.jetwhale.tools.docsscreenshots.DocsShot
import com.kitakkun.jetwhale.tools.docsscreenshots.DocsShotRecorder
import com.kitakkun.jetwhale.tools.docsscreenshots.InMemoryPluginStorage
import com.kitakkun.jetwhale.tools.docsscreenshots.PluginSceneSurface
import com.kitakkun.jetwhale.tools.docsscreenshots.mouseClickThenMovePointerAway
import com.kitakkun.jetwhale.tools.docsscreenshots.onSurface
import kotlin.test.Test

/**
 * The inspector is laid out wider than the page (1.6 px per dp rather than 2): its detail pane gets
 * 42% of the width, and the attribute editors need more than that of 688 dp.
 */
@OptIn(ExperimentalTestApi::class)
class ComposeSemanticsInspectorDocsScreenshots {
    private val recorder = DocsShotRecorder.forImagesDirectoryProperty()

    @Test
    fun `the tree with a button selected`() = recorder.record(
        DocsShot(page = PAGE, name = "tree", surfaceSize = DpSize(860.dp, 440.dp), density = 1.6f, displayWidth = 688),
    ) { darkTheme ->
        setInspector(darkTheme)
        onNodeWithText("Button · Add to cart").mouseClickThenMovePointerAway()
        onSurface()
    }

    @Test
    fun `the attributes of a selected View`() = recorder.record(
        DocsShot(page = PAGE, name = "view-attributes", surfaceSize = DpSize(860.dp, 560.dp), density = 1.6f, displayWidth = 688),
    ) { darkTheme ->
        setInspector(darkTheme)
        onNodeWithText("TextView · @id/promo_banner · Free shipping on orders over $30").mouseClickThenMovePointerAway()
        // performScrollTo stops as soon as the header is in view, at the pane's bottom edge;
        // scrolling by its offset brings it to the top, so the attributes below it are in the
        // picture.
        val detailPane = onNode(hasScrollAction() and hasAnyDescendant(hasText(VIEW_ATTRIBUTES_HEADER)))
        val headerTop = onNodeWithText(VIEW_ATTRIBUTES_HEADER).fetchSemanticsNode().boundsInRoot.top
        val paneTop = detailPane.fetchSemanticsNode().boundsInRoot.top
        detailPane.performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy -> scrollBy(0f, headerTop - paneTop) }
        onSurface()
    }
}

@OptIn(ExperimentalTestApi::class)
private fun SkikoComposeUiTest.setInspector(darkTheme: Boolean) {
    setContent {
        PluginSceneSurface(darkTheme = darkTheme, storage = InMemoryPluginStorage(emptyMap())) {
            ComposeSemanticsInspectorScreenRoot(
                snapshot = SNAPSHOT,
                capturing = false,
                roundTripMs = 31,
                errorMessage = null,
                actionStatus = null,
                highlightStatus = null,
                viewAttributes = PROMO_BANNER_ATTRIBUTES,
                onCapture = {},
                onPerformAction = {},
                onSelectedNodeChange = {},
                onHighlightTargetChange = {},
                onCommitViewAttribute = { _, _ -> },
            )
        }
    }
}

private const val PAGE = "compose-semantics-inspector"

private const val VIEW_ATTRIBUTES_HEADER = "View attributes"

private const val SCREEN_WIDTH_PX = 1080f

private const val SCREEN_HEIGHT_PX = 2400f

private val PROMO_BANNER_BOUNDS = NodeBounds(left = 0f, top = 1520f, right = SCREEN_WIDTH_PX, bottom = 1640f)

/** A Compose screen of the Sample App inside the Android views that host it, one of them embedded. */
private val SNAPSHOT = NodeTreeSnapshot(
    capturedAtMs = 0,
    captureDurationMs = 14,
    options = NodeTreeCaptureOptions(),
    roots = listOf(
        ComposeRoot(
            rootId = "MainActivity",
            label = "MainActivity",
            density = 2.75f,
            windowOffsetX = 0f,
            windowOffsetY = 0f,
            node = ViewNode(
                id = -1,
                viewClass = "com.android.internal.policy.DecorView",
                bounds = NodeBounds(left = 0f, top = 0f, right = SCREEN_WIDTH_PX, bottom = SCREEN_HEIGHT_PX),
                boundsInScreen = NodeBounds(left = 0f, top = 0f, right = SCREEN_WIDTH_PX, bottom = SCREEN_HEIGHT_PX),
                children = listOf(
                    ViewNode(
                        id = -2,
                        viewClass = "androidx.compose.ui.platform.ComposeView",
                        bounds = NodeBounds(left = 0f, top = 0f, right = SCREEN_WIDTH_PX, bottom = SCREEN_HEIGHT_PX),
                        boundsInScreen = NodeBounds(left = 0f, top = 0f, right = SCREEN_WIDTH_PX, bottom = SCREEN_HEIGHT_PX),
                        children = listOf(
                            composeNode(
                                id = 1,
                                bounds = NodeBounds(left = 0f, top = 0f, right = SCREEN_WIDTH_PX, bottom = SCREEN_HEIGHT_PX),
                                role = null,
                                text = null,
                                contentDescription = null,
                                testTag = null,
                                actions = emptyList(),
                                children = listOf(
                                    composeNode(
                                        id = 2,
                                        bounds = NodeBounds(left = 48f, top = 140f, right = 600f, bottom = 220f),
                                        role = null,
                                        text = "Sample App",
                                        contentDescription = null,
                                        testTag = null,
                                        actions = emptyList(),
                                        children = emptyList(),
                                    ),
                                    composeNode(
                                        id = 3,
                                        bounds = NodeBounds(left = 940f, top = 120f, right = 1060f, bottom = 240f),
                                        role = "Button",
                                        text = null,
                                        contentDescription = "Search",
                                        testTag = null,
                                        actions = listOf("OnClick"),
                                        children = emptyList(),
                                    ),
                                    composeNode(
                                        id = 4,
                                        bounds = NodeBounds(left = 0f, top = 280f, right = SCREEN_WIDTH_PX, bottom = 1480f),
                                        role = null,
                                        text = null,
                                        contentDescription = null,
                                        testTag = "product_list",
                                        actions = listOf("ScrollBy", "ScrollToIndex"),
                                        children = listOf(
                                            productRow(id = 41, name = "Blue mug", top = 280f),
                                            productRow(id = 42, name = "Notebook", top = 500f),
                                            productRow(id = 43, name = "Water bottle", top = 720f),
                                        ),
                                    ),
                                    ViewNode(
                                        id = -3,
                                        viewClass = "android.widget.TextView",
                                        resourceId = "promo_banner",
                                        text = "Free shipping on orders over $30",
                                        bounds = PROMO_BANNER_BOUNDS,
                                        boundsInScreen = PROMO_BANNER_BOUNDS,
                                    ),
                                    composeNode(
                                        id = 5,
                                        bounds = NodeBounds(left = 48f, top = 1700f, right = 1032f, bottom = 1820f),
                                        role = "Checkbox",
                                        text = "Gift wrap",
                                        contentDescription = null,
                                        testTag = null,
                                        actions = listOf("OnClick"),
                                        children = emptyList(),
                                    ).copy(toggleableState = "Off"),
                                    composeNode(
                                        id = 6,
                                        bounds = NodeBounds(left = 48f, top = 2160f, right = 1032f, bottom = 2300f),
                                        role = "Button",
                                        text = "Add to cart",
                                        contentDescription = null,
                                        testTag = "add_to_cart",
                                        actions = listOf("OnClick"),
                                        children = emptyList(),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    ),
)

private fun composeNode(
    id: Int,
    bounds: NodeBounds,
    role: String?,
    text: String?,
    contentDescription: String?,
    testTag: String?,
    actions: List<String>,
    children: List<UiNode>,
) = ComposeNode(
    id = id,
    role = role,
    text = text,
    contentDescription = contentDescription,
    testTag = testTag,
    bounds = bounds,
    boundsInScreen = bounds,
    actions = actions,
    isClickable = "OnClick" in actions,
    isScrollable = "ScrollBy" in actions,
    children = children,
)

private fun productRow(id: Int, name: String, top: Float) = composeNode(
    id = id,
    bounds = NodeBounds(left = 0f, top = top, right = SCREEN_WIDTH_PX, bottom = top + 220f),
    role = null,
    text = name,
    contentDescription = null,
    testTag = "product_$id",
    actions = listOf("OnClick"),
    children = emptyList(),
)

private val PADDING_LEFT = ViewAttributeValue.DimensionValue(px = 44f, dp = 16f)

/** What the agent reads from the promo banner: a `TextView`, so it has the Text group as well. */
private val PROMO_BANNER_ATTRIBUTES = ViewAttributesUiState(
    attributes = listOf(
        attribute("visibility", "State", ViewAttributeValue.EnumValue(value = "VISIBLE", options = listOf("VISIBLE", "INVISIBLE", "GONE")), editable = true),
        attribute("enabled", "State", ViewAttributeValue.BooleanValue(true), editable = true),
        attribute("clickable", "State", ViewAttributeValue.BooleanValue(false), editable = true),
        attribute(
            "layout.width",
            "Layout",
            ViewAttributeValue.LayoutSizeValue(constant = "MATCH_PARENT", px = null, dp = null, constants = listOf("MATCH_PARENT", "WRAP_CONTENT")),
            editable = true,
        ),
        attribute("padding.left", "Layout", PADDING_LEFT, editable = true),
        attribute("alpha", "Appearance", ViewAttributeValue.FloatValue(1f), editable = true),
        attribute("backgroundColor", "Appearance", ViewAttributeValue.ColorValue(argb = 0xFFFFF3C4.toInt()), editable = true),
        attribute("text", "Text", ViewAttributeValue.TextValue("Free shipping on orders over $30"), editable = true),
        attribute("textSize", "Text", ViewAttributeValue.DimensionValue(px = 38.5f, dp = 14f), editable = true),
        attribute("id", "Info", ViewAttributeValue.TextValue("@id/promo_banner"), editable = false),
        attribute("class", "Info", ViewAttributeValue.TextValue("android.widget.TextView"), editable = false),
    ),
    message = null,
    writeStatus = "padding.left: ${PADDING_LEFT.asText()}",
    writeFailed = false,
)

private fun attribute(id: String, group: String, value: ViewAttributeValue, editable: Boolean) = ViewAttribute(id = id, label = id, group = group, value = value, editable = editable)
