package com.kitakkun.jetwhale.plugins.semantics.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.HtmlElementView
import androidx.compose.ui.window.ComposeViewport
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeRoot
import com.kitakkun.jetwhale.plugins.semantics.protocol.HtmlNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalComposeUiApi::class)
class HtmlInteropContainerTest {
    private val viewportContainers = mutableListOf<HTMLElement>()
    private var probe: AutoCloseable? = null

    @BeforeTest
    fun installProbe() {
        probe = installJetWhaleSemanticsProbe()
    }

    @AfterTest
    fun removeViewportsAndProbe() {
        probe?.close()
        ComposeNodeSourceRegistry.clear()
        viewportContainers.forEach(HTMLElement::remove)
    }

    @Test
    fun `an embedded element follows the Compose content as a child of the root node`() = runTestInRealTime {
        val field = (document.createElement("input") as HTMLInputElement)
        val root = renderAndCapture(embeddedElements = listOf(field)) {
            Column {
                BasicText("Compose", Modifier.testTag("label"))
                HtmlElementView(factory = { field }, modifier = Modifier.size(120.dp, 30.dp))
            }
        }

        val rootChildren = root.node?.children.orEmpty()
        assertEquals("label", (rootChildren.first() as? ComposeNode)?.testTag)
        val fieldNode = rootChildren.last() as? HtmlNode ?: fail("the root's last child is not an HTML node: $rootChildren")
        assertEquals("input", fieldNode.tagName, "the div Compose wraps the element in is left out")
        assertTrue(fieldNode.id < 0, "an HTML node's id is negative")
    }

    @Test
    fun `an HTML node reports the element's tag, id, classes, attributes, content and bounds`() = runTestInRealTime {
        val field = (document.createElement("input") as HTMLInputElement).apply {
            id = "name"
            className = "field wide"
            type = "text"
            name = "full-name"
            placeholder = "Your name"
            value = "Ada"
            setAttribute("aria-label", "Name")
            // Compose sizes the element to 100% of its wrapper, which an input's padding and border
            // would otherwise overflow.
            style.setProperty("box-sizing", "border-box")
        }
        val root = renderAndCapture(embeddedElements = listOf(field)) {
            Column {
                Box(Modifier.size(20.dp))
                HtmlElementView(factory = { field }, modifier = Modifier.size(120.dp, 30.dp))
            }
        }

        val node = root.htmlNodeWithElementId("name")
        assertEquals(listOf("field", "wide"), node.cssClasses)
        assertEquals(mapOf("type" to "text", "name" to "full-name", "placeholder" to "Your name"), node.attributes)
        assertEquals("Ada", node.editableText)
        assertEquals("Name", node.contentDescription)
        assertTrue(node.isEditable)
        assertTrue(node.isClickable)
        assertEquals(listOf("OnClick", "SetText", "InsertTextAtCursor"), node.actions)
        assertEquals(NodeBounds(left = 0f, top = 20f, right = 120f, bottom = 50f), node.bounds)
        assertEquals(NodeBounds(left = VIEWPORT_LEFT, top = VIEWPORT_TOP + 20f, right = VIEWPORT_LEFT + 120f, bottom = VIEWPORT_TOP + 50f), node.boundsInScreen)
    }

    @Test
    fun `the elements inside an embedded element are its children`() = runTestInRealTime {
        val panel = (document.createElement("div") as HTMLElement).apply {
            id = "panel"
            innerHTML = "<button id=\"save\">Save</button><span id=\"note\">  Saved   twice </span>"
        }
        val root = renderAndCapture(embeddedElements = listOf(panel)) {
            HtmlElementView(factory = { panel }, modifier = Modifier.size(200.dp, 40.dp))
        }

        val panelNode = root.htmlNodeWithElementId("panel")
        assertEquals(listOf("save", "note"), panelNode.children.map { (it as HtmlNode).elementId })
        val saveButton = root.htmlNodeWithElementId("save")
        assertEquals("Save", saveButton.text)
        assertTrue(saveButton.isClickable)
        assertEquals("Saved twice", root.htmlNodeWithElementId("note").text)
        assertFalse(root.htmlNodeWithElementId("note").isClickable)
    }

    @Test
    fun `the depth limit counts the embedded elements from the root node`() = runTestInRealTime {
        val panel = (document.createElement("div") as HTMLElement).apply {
            id = "panel"
            innerHTML = "<button id=\"save\">Save</button>"
        }
        renderAndCapture(embeddedElements = listOf(panel)) {
            HtmlElementView(factory = { panel }, modifier = Modifier.size(200.dp, 40.dp))
        }

        val shallow = viewportSource().capture(NodeTreeCaptureOptions(maxDepth = 1))
        val rootOnly = viewportSource().capture(NodeTreeCaptureOptions(maxDepth = 0))

        assertEquals(emptyList(), shallow.htmlNodeWithElementId("panel").children)
        assertEquals(emptyList(), rootOnly?.node?.children)
    }

    @Test
    fun `a masked field is editable but its content is not read`() = runTestInRealTime {
        val passwordField = (document.createElement("input") as HTMLInputElement).apply {
            id = "password"
            type = "password"
            value = "hunter2"
        }
        val maskedByStyle = (document.createElement("input") as HTMLInputElement).apply {
            id = "pin"
            value = "1234"
            style.setProperty("-webkit-text-security", "disc")
        }
        val root = renderAndCapture(embeddedElements = listOf(passwordField, maskedByStyle)) {
            Column {
                HtmlElementView(factory = { passwordField }, modifier = Modifier.size(120.dp, 30.dp))
                HtmlElementView(factory = { maskedByStyle }, modifier = Modifier.size(120.dp, 30.dp))
            }
        }

        for (elementId in listOf("password", "pin")) {
            val fieldNode = root.htmlNodeWithElementId(elementId)
            assertTrue(fieldNode.isEditable, elementId)
            assertNull(fieldNode.editableText, elementId)
            assertNull(fieldNode.text, elementId)
        }
    }

    @Test
    fun `an element Compose clips shows on screen only as far as the clip`() = runTestInRealTime {
        val banner = (document.createElement("div") as HTMLElement).apply { id = "banner" }
        val root = renderAndCapture(embeddedElements = listOf(banner)) {
            Box(Modifier.size(120.dp, 10.dp).clipToBounds()) {
                Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)) {
                    HtmlElementView(factory = { banner }, modifier = Modifier.size(120.dp, 30.dp))
                }
            }
        }

        val node = root.htmlNodeWithElementId("banner")
        assertEquals(30f, node.bounds.height)
        assertEquals(NodeBounds(left = VIEWPORT_LEFT, top = VIEWPORT_TOP, right = VIEWPORT_LEFT + 120f, bottom = VIEWPORT_TOP + 10f), node.boundsInScreen)
    }

    @Test
    fun `Click runs the element's own behavior`() = runTestInRealTime {
        val checkbox = (document.createElement("input") as HTMLInputElement).apply {
            id = "agree"
            type = "checkbox"
        }
        val root = renderAndCapture(embeddedElements = listOf(checkbox)) {
            HtmlElementView(factory = { checkbox }, modifier = Modifier.size(20.dp))
        }
        val before = root.htmlNodeWithElementId("agree")

        val result = performNodeAction(nodeId = before.id, action = NodeAction.Click)
        val after = viewportSource().capture(NodeTreeCaptureOptions()).htmlNodeWithElementId("agree")

        assertTrue(result.performed, result.message)
        assertEquals("Off", before.toggleableState)
        assertEquals("On", after.toggleableState)
        assertEquals(before.id, after.id, "the element keeps its id while it stays in the page")
    }

    @Test
    fun `Click is refused on a disabled element and sent with a note where the page shows no click handler`() = runTestInRealTime {
        val disabledButton = (document.createElement("button") as HTMLElement).apply {
            id = "disabled"
            setAttribute("disabled", "")
        }
        var plainDivClicks = 0
        val plainDiv = (document.createElement("div") as HTMLElement).apply {
            id = "plain"
            addEventListener("click", { plainDivClicks++ })
        }
        val root = renderAndCapture(embeddedElements = listOf(disabledButton, plainDiv)) {
            Column {
                HtmlElementView(factory = { disabledButton }, modifier = Modifier.size(60.dp, 20.dp))
                HtmlElementView(factory = { plainDiv }, modifier = Modifier.size(60.dp, 20.dp))
            }
        }

        val disabledResult = performNodeAction(nodeId = root.htmlNodeWithElementId("disabled").id, action = NodeAction.Click)
        val plainResult = performNodeAction(nodeId = root.htmlNodeWithElementId("plain").id, action = NodeAction.Click)

        assertFalse(disabledResult.performed)
        assertFalse(root.htmlNodeWithElementId("disabled").isEnabled)
        assertTrue(plainResult.performed, plainResult.message)
        assertTrue(plainResult.message.orEmpty().contains("does not show whether this element takes a click"), plainResult.message)
        assertEquals(1, plainDivClicks)
    }

    @Test
    fun `SetText reaches the field under a value property a framework defines on it and fires input and change`() = runTestInRealTime {
        val field = (document.createElement("input") as HTMLInputElement).apply { id = "name" }
        val firedEventTypes = mutableListOf<String>()
        field.addEventListener("input", { firedEventTypes += it.type })
        field.addEventListener("change", { firedEventTypes += it.type })
        defineValuePropertyIgnoringWrites(field)
        val root = renderAndCapture(embeddedElements = listOf(field)) {
            HtmlElementView(factory = { field }, modifier = Modifier.size(120.dp, 30.dp))
        }

        val result = viewportSource().performAction(
            PerformNodeAction(rootId = viewportSource().sourceId, nodeId = root.htmlNodeWithElementId("name").id, action = NodeAction.SetText, text = "Grace"),
        )

        assertTrue(result.performed, result.message)
        assertEquals("Grace", field.value)
        assertEquals(listOf("input", "change"), firedEventTypes)
        assertEquals(field, document.activeElement, "the field takes focus as it would for typing")
    }

    @Test
    fun `InsertText goes in at the field's cursor`() = runTestInRealTime {
        val field = (document.createElement("input") as HTMLInputElement).apply {
            id = "greeting"
            value = "Helo"
        }
        val root = renderAndCapture(embeddedElements = listOf(field)) {
            HtmlElementView(factory = { field }, modifier = Modifier.size(120.dp, 30.dp))
        }
        field.setSelectionRange(3, 3)

        val result = viewportSource().performAction(
            PerformNodeAction(rootId = viewportSource().sourceId, nodeId = root.htmlNodeWithElementId("greeting").id, action = NodeAction.InsertText, text = "l"),
        )

        assertTrue(result.performed, result.message)
        assertEquals("Hello", field.value)
    }

    @Test
    fun `SetText replaces a contenteditable element's text and fires input`() = runTestInRealTime {
        val editor = (document.createElement("div") as HTMLElement).apply {
            id = "editor"
            setAttribute("contenteditable", "true")
            innerHTML = "<b>old</b> text"
        }
        val firedEventTypes = mutableListOf<String>()
        editor.addEventListener("input", { firedEventTypes += it.type })
        val root = renderAndCapture(embeddedElements = listOf(editor)) {
            HtmlElementView(factory = { editor }, modifier = Modifier.size(120.dp, 30.dp))
        }
        val before = root.htmlNodeWithElementId("editor")

        val result = viewportSource().performAction(
            PerformNodeAction(rootId = viewportSource().sourceId, nodeId = before.id, action = NodeAction.SetText, text = "new"),
        )

        assertTrue(result.performed, result.message)
        assertEquals("old text", before.editableText)
        assertEquals(emptyList(), before.children, "a field's content is its text, not nodes of its own")
        assertEquals("new", editor.textContent)
        assertEquals(listOf("input"), firedEventTypes)
    }

    @Test
    fun `a capture right after SetText already shows what the page's listener changed in Compose`() = runTestInRealTime {
        val field = (document.createElement("input") as HTMLInputElement).apply { id = "name" }
        val root = renderAndCapture(embeddedElements = listOf(field)) {
            var enteredName by remember { mutableStateOf("") }
            remember { field.addEventListener("input", { enteredName = field.value }) }
            Column {
                BasicText("Hello, $enteredName", Modifier.testTag("greeting"))
                HtmlElementView(factory = { field }, modifier = Modifier.size(120.dp, 30.dp))
            }
        }

        viewportSource().performAction(
            PerformNodeAction(rootId = viewportSource().sourceId, nodeId = root.htmlNodeWithElementId("name").id, action = NodeAction.SetText, text = "Grace"),
        )
        val after = viewportSource().capture(NodeTreeCaptureOptions())

        assertEquals("Hello, Grace", after.nodeWithTag("greeting").text)
    }

    @Test
    fun `the highlight box covers an HTML node`() = runTestInRealTime {
        val banner = (document.createElement("div") as HTMLElement).apply { id = "banner" }
        val viewportContainer = renderViewport {
            Column {
                Box(Modifier.size(20.dp))
                HtmlElementView(factory = { banner }, modifier = Modifier.size(60.dp, 30.dp))
            }
        }
        val node = captureOnceEmbeddedElementsPlaced(viewportContainer, listOf(banner)).htmlNodeWithElementId("banner")
        val source = viewportSource() as NodeHighlightSource

        val highlightResult = source.highlight(nodeId = node.id, ttl = 30.seconds)
        val boxRect = viewportContainer.viewportShadowRoot()
            ?.querySelectorAll("div")?.let { divs -> (0 until divs.length).mapNotNull { divs.item(it) as? HTMLElement } }.orEmpty()
            .single { it.style.zIndex == "2147483647" }
            .getBoundingClientRect()
        source.highlight(nodeId = null, ttl = Duration.ZERO)

        assertTrue(highlightResult.shown, highlightResult.message)
        assertEquals(
            node.boundsInScreen,
            NodeBounds(left = boxRect.left.toFloat(), top = boxRect.top.toFloat(), right = boxRect.right.toFloat(), bottom = boxRect.bottom.toFloat()),
        )
    }

    private fun runTestInRealTime(block: suspend CoroutineScope.() -> Unit): TestResult = runTest(timeout = 30.seconds) {
        // A capture waits in real time for Compose to write its accessibility DOM, which runTest's
        // virtual clock would skip past.
        withContext(Dispatchers.Default, block)
    }

    private fun renderViewport(content: @Composable () -> Unit): HTMLElement {
        val viewportContainer = document.createElement("div") as HTMLElement
        viewportContainer.style.cssText = "position: absolute; left: ${VIEWPORT_LEFT}px; top: ${VIEWPORT_TOP}px; width: 300px; height: 200px;"
        requireNotNull(document.body).appendChild(viewportContainer)
        viewportContainers += viewportContainer
        ComposeViewport(viewportContainer, configure = {}, content = content)
        return viewportContainer
    }

    /** Renders [content] and captures it once Compose has placed every element of [embeddedElements] over the canvas. */
    private suspend fun renderAndCapture(embeddedElements: List<Element>, content: @Composable () -> Unit): ComposeRoot {
        val viewportContainer = renderViewport(content)
        return captureOnceEmbeddedElementsPlaced(viewportContainer, embeddedElements)
    }

    /**
     * Captures the viewport in [viewportContainer] once Compose has written its accessibility DOM and
     * shown the wrapper of every element in [embeddedElements], which it keeps hidden until it has
     * positioned it.
     */
    private suspend fun captureOnceEmbeddedElementsPlaced(viewportContainer: HTMLElement, embeddedElements: List<Element>): ComposeRoot {
        withTimeoutOrNull(STEP_TIMEOUT) { awaitCondition(viewportContainer) { viewportContainer.viewportShadowRoot() != null } }
            ?: fail("ComposeViewport built no shadow root")
        val shadowRoot = viewportContainer.viewportShadowRoot() ?: fail("the viewport has no shadow root")
        withTimeoutOrNull(STEP_TIMEOUT) { awaitCondition(shadowRoot) { shadowRoot.querySelector("#cmp_a11y_root")?.firstElementChild != null } }
            ?: fail("Compose wrote no accessibility DOM")
        withTimeoutOrNull(STEP_TIMEOUT) {
            awaitCondition(viewportContainer) { embeddedElements.all { (it.parentElement as? HTMLElement)?.style?.visibility == "visible" } }
        } ?: fail("Compose did not show every embedded element: ${embeddedElements.map { (it.parentElement as? HTMLElement)?.style?.cssText }}")
        return viewportSource().capture(NodeTreeCaptureOptions()) ?: fail("the viewport reported no root")
    }

    private fun viewportSource(): ComposeNodeSource = ComposeNodeSourceRegistry.sources.single()

    private suspend fun performNodeAction(nodeId: Int, action: NodeAction): NodeActionResult = viewportSource().performAction(PerformNodeAction(rootId = viewportSource().sourceId, nodeId = nodeId, action = action))
}

private val STEP_TIMEOUT: Duration = 20.seconds

private fun ComposeRoot?.htmlNodeWithElementId(elementId: String): HtmlNode = this?.allNodes().orEmpty().filterIsInstance<HtmlNode>().firstOrNull { it.elementId == elementId } ?: fail("no HTML node with id $elementId in $this")

/**
 * Gives [field] a `value` property of its own that reads the field and drops every write, the way
 * React's value tracking would hide a write made through it.
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun defineValuePropertyIgnoringWrites(field: Element) {
    js("Object.defineProperty(field, 'value', { configurable: true, get: function() { return Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').get.call(this); }, set: function(value) {} })")
}
