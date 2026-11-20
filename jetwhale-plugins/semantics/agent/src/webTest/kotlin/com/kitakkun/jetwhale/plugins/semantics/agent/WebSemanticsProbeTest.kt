package com.kitakkun.jetwhale.plugins.semantics.agent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeRoot
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.UiNode
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.MutationObserver
import org.w3c.dom.Node
import org.w3c.dom.OPEN
import org.w3c.dom.ShadowRoot
import org.w3c.dom.ShadowRootInit
import org.w3c.dom.ShadowRootMode
import org.w3c.dom.asList
import kotlin.coroutines.resume
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class WebSemanticsProbeTest {
    private val containers = mutableListOf<HTMLElement>()
    private var probe: AutoCloseable? = null

    @AfterTest
    fun removeViewportsAndProbe() {
        probe?.close()
        ComposeNodeSourceRegistry.clear()
        containers.forEach(HTMLElement::remove)
    }

    @Test
    fun `a capture reports what the accessibility DOM holds for each node`() = runTestInRealTime {
        val container = renderViewport(isA11YEnabled = true) {
            Column {
                BasicText("Hello", Modifier.testTag("greeting"))
                BasicText("first\nsecond", Modifier.testTag("two-lines"))
                Box(Modifier.size(40.dp).testTag("send").semantics { contentDescription = "Send" }.clickable {})
                Box(Modifier.size(10.dp).testTag("logo").semantics { role = Role.Image })
            }
        }
        probe = installJetWhaleSemanticsProbe()

        val root = captureOnceTaggedNodeWritten(container, testTag = "logo")

        val greeting = root.nodeWithTag("greeting")
        assertEquals("Hello", greeting.text)
        assertFalse(greeting.isClickable)
        assertEquals("first\nsecond", root.nodeWithTag("two-lines").text)
        val send = root.nodeWithTag("send")
        assertEquals("Send", send.contentDescription)
        assertEquals("Button", send.role)
        assertTrue(send.isClickable)
        assertEquals(listOf("OnClick"), send.actions)
        assertEquals("Image", root.nodeWithTag("logo").role)
        assertTrue(root.allNodes().all { it.id >= 0 }, "a Compose node's id is never negative")
    }

    @Test
    fun `bounds are CSS pixels from the composition and on screen from the browser viewport`() = runTestInRealTime {
        val container = renderViewport(isA11YEnabled = true) {
            Column {
                Box(Modifier.size(20.dp).testTag("first"))
                Box(Modifier.size(40.dp, 30.dp).testTag("second"))
            }
        }
        probe = installJetWhaleSemanticsProbe()

        val root = captureOnceTaggedNodeWritten(container, testTag = "second")

        assertEquals(1f, root.density)
        assertEquals(VIEWPORT_LEFT, root.windowOffsetX)
        assertEquals(VIEWPORT_TOP, root.windowOffsetY)
        val second = root.nodeWithTag("second")
        assertEquals(NodeBounds(left = 0f, top = 20f, right = 40f, bottom = 50f), second.bounds)
        assertEquals(NodeBounds(left = VIEWPORT_LEFT, top = VIEWPORT_TOP + 20f, right = VIEWPORT_LEFT + 40f, bottom = VIEWPORT_TOP + 50f), second.boundsInScreen)
    }

    @Test
    fun `a capture right after a click already shows what the click did`() = runTestInRealTime {
        val container = renderViewport(isA11YEnabled = true) { Counter() }
        probe = installJetWhaleSemanticsProbe()
        val before = captureOnceTaggedNodeWritten(container, testTag = "counter").nodeWithTag("counter")

        val result = performNodeAction(nodeId = before.id, action = NodeAction.Click)
        val after = viewportSource().capture(NodeTreeCaptureOptions()).nodeWithTag("counter")

        assertTrue(result.performed, result.message)
        assertEquals("Count 0", before.text)
        assertEquals("Count 1", after.text)
        assertEquals(before.id, after.id, "the node keeps its id while it stays composed")
    }

    @Test
    fun `a click runs the node it names and not the clickable node around it`() = runTestInRealTime {
        val container = renderViewport(isA11YEnabled = true) {
            var rowClicks by remember { mutableIntStateOf(0) }
            var buttonClicks by remember { mutableIntStateOf(0) }
            Column {
                Column(Modifier.clickable { rowClicks++ }) {
                    BasicText("Button", Modifier.testTag("button").clickable { buttonClicks++ })
                }
                BasicText("row=$rowClicks button=$buttonClicks", Modifier.testTag("clicks"))
            }
        }
        probe = installJetWhaleSemanticsProbe()
        val button = captureOnceTaggedNodeWritten(container, testTag = "button").nodeWithTag("button")

        performNodeAction(nodeId = button.id, action = NodeAction.Click)

        assertEquals("row=0 button=1", viewportSource().capture(NodeTreeCaptureOptions()).nodeWithTag("clicks").text)
    }

    @Test
    fun `a click reaches a clickable heading that the page marks only as a heading`() = runTestInRealTime {
        val container = renderViewport(isA11YEnabled = true) {
            var clicks by remember { mutableIntStateOf(0) }
            Column {
                BasicText("Section", Modifier.testTag("section").semantics { heading() }.clickable { clicks++ })
                BasicText("clicks=$clicks", Modifier.testTag("clicks"))
            }
        }
        probe = installJetWhaleSemanticsProbe()
        val section = captureOnceTaggedNodeWritten(container, testTag = "section").nodeWithTag("section")

        val result = performNodeAction(nodeId = section.id, action = NodeAction.Click)

        assertFalse(section.isClickable, "the heading role hides the click from a capture")
        assertTrue(result.performed, result.message)
        assertEquals("clicks=1", viewportSource().capture(NodeTreeCaptureOptions()).nodeWithTag("clicks").text)
    }

    @Test
    fun `a shallow capture keeps the ids of the nodes it did not reach`() = runTestInRealTime {
        val container = renderViewport(isA11YEnabled = true) { Counter() }
        probe = installJetWhaleSemanticsProbe()
        val counter = captureOnceTaggedNodeWritten(container, testTag = "counter").nodeWithTag("counter")

        viewportSource().capture(NodeTreeCaptureOptions(maxDepth = 0))
        val result = performNodeAction(nodeId = counter.id, action = NodeAction.Click)

        assertTrue(result.performed, result.message)
        assertEquals(counter.id, viewportSource().capture(NodeTreeCaptureOptions()).nodeWithTag("counter").id)
    }

    @Test
    fun `a field is editable and clickable but its content is not read`() = runTestInRealTime {
        val container = renderViewport(isA11YEnabled = true) {
            BasicTextField(rememberTextFieldState("hunter2"), Modifier.testTag("password"))
        }
        probe = installJetWhaleSemanticsProbe()

        val passwordField = captureOnceTaggedNodeWritten(container, testTag = "password").nodeWithTag("password")

        assertTrue(passwordField.isEditable)
        assertTrue(passwordField.isClickable)
        assertNull(passwordField.text)
        assertNull(passwordField.editableText)
    }

    @Test
    fun `an action other than a click is refused with the reason`() = runTestInRealTime {
        val container = renderViewport(isA11YEnabled = true) { Counter() }
        probe = installJetWhaleSemanticsProbe()
        val counter = captureOnceTaggedNodeWritten(container, testTag = "counter").nodeWithTag("counter")

        val result = performNodeAction(nodeId = counter.id, action = NodeAction.LongClick)

        assertFalse(result.performed)
        assertTrue(result.message.orEmpty().contains("not available on the web"), result.message)
    }

    @Test
    fun `a viewport created after the install is registered and one that leaves the page is not`() = runTestInRealTime {
        probe = installJetWhaleSemanticsProbe()
        val container = renderViewport(isA11YEnabled = true) { Counter() }

        captureOnceTaggedNodeWritten(container, testTag = "counter")
        // The browser notifies mutation observers in the order they were created, so the probe's
        // observer has unregistered the viewport before this one fires.
        val sourceCountWhileShown = ComposeNodeSourceRegistry.sources.size
        awaitMutation(document, container::remove)

        assertEquals(1, sourceCountWhileShown)
        assertEquals(emptyList(), ComposeNodeSourceRegistry.sources)
    }

    @Test
    fun `a node that an element around the canvas clips away is not visible`() = runTestInRealTime {
        val clippingContainer = renderViewportClippedTo(heightPx = 15) {
            Column {
                Box(Modifier.size(20.dp).testTag("first"))
                Box(Modifier.size(40.dp, 30.dp).testTag("second"))
            }
        }
        probe = installJetWhaleSemanticsProbe()

        val root = captureOnceTaggedNodeWritten(clippingContainer, testTag = "second")

        assertEquals(NodeBounds(left = VIEWPORT_LEFT, top = VIEWPORT_TOP, right = VIEWPORT_LEFT + 20f, bottom = VIEWPORT_TOP + 15f), root.nodeWithTag("first").boundsInScreen)
        assertTrue(root.allNodes().none { (it as? ComposeNode)?.testTag == "second" }, "a node clipped away entirely is left out")
    }

    @Test
    fun `the nodes of a viewport that CSS hides are not visible`() = runTestInRealTime {
        val container = renderViewport(isA11YEnabled = true) { Counter() }
        probe = installJetWhaleSemanticsProbe()
        captureOnceTaggedNodeWritten(container, testTag = "counter")

        container.style.visibility = "hidden"
        val withInvisible = viewportSource().capture(NodeTreeCaptureOptions(includeInvisible = true))
        val withoutInvisible = viewportSource().capture(NodeTreeCaptureOptions())

        assertFalse(withInvisible.nodeWithTag("counter").isVisible)
        assertNull(withoutInvisible?.node)
    }

    @Test
    fun `a viewport removed before the probe hears of it is not registered`() = runTestInRealTime {
        probe = installJetWhaleSemanticsProbe()
        val shadowHost = viewportShadowHostWithoutCompose()

        awaitMutation(document) {
            requireNotNull(document.body).appendChild(shadowHost)
            shadowHost.remove()
        }

        assertEquals(emptyList(), ComposeNodeSourceRegistry.sources)
    }

    @Test
    fun `a viewport without an accessibility DOM says why its capture is empty`() = runTestInRealTime {
        val container = renderViewport(isA11YEnabled = false) { Counter() }
        awaitShadowRoot(container)
        probe = installJetWhaleSemanticsProbe()

        val failure = assertFailsWith<IllegalStateException> { viewportSource().capture(NodeTreeCaptureOptions()) }

        assertTrue(failure.message.orEmpty().contains("isA11YEnabled = false"), failure.message)
    }

    @Test
    fun `the highlight box covers the node until it is cleared`() = runTestInRealTime {
        val container = renderViewport(isA11YEnabled = true) {
            Column {
                Box(Modifier.size(20.dp))
                Box(Modifier.size(40.dp, 30.dp).testTag("target"))
            }
        }
        probe = installJetWhaleSemanticsProbe()
        val target = captureOnceTaggedNodeWritten(container, testTag = "target").nodeWithTag("target")
        val source = viewportSource() as NodeHighlightSource

        val highlightResult = source.highlight(nodeId = target.id, ttl = 30.seconds)
        val boxRect = highlightBoxesIn(container).single().getBoundingClientRect()
        source.highlight(nodeId = null, ttl = Duration.ZERO)

        assertTrue(highlightResult.shown, highlightResult.message)
        assertEquals(
            target.boundsInScreen,
            NodeBounds(left = boxRect.left.toFloat(), top = boxRect.top.toFloat(), right = boxRect.right.toFloat(), bottom = boxRect.bottom.toFloat()),
        )
        assertTrue(highlightBoxesIn(container).isEmpty(), "clearing takes the box down")
    }

    private fun runTestInRealTime(block: suspend CoroutineScope.() -> Unit): TestResult = runTest(timeout = 30.seconds) {
        // A capture waits in real time for Compose to write its accessibility DOM, which runTest's
        // virtual clock would skip past.
        withContext(Dispatchers.Default, block)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun renderViewport(isA11YEnabled: Boolean, content: @Composable () -> Unit): HTMLElement {
        val container = document.createElement("div") as HTMLElement
        container.style.cssText = "position: absolute; left: ${VIEWPORT_LEFT}px; top: ${VIEWPORT_TOP}px; width: 300px; height: 200px;"
        requireNotNull(document.body).appendChild(container)
        containers += container
        ComposeViewport(container, configure = { this.isA11YEnabled = isA11YEnabled }, content = content)
        return container
    }

    /** Renders a viewport the size [renderViewport] gives it, inside an element that clips it to [heightPx]. */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun renderViewportClippedTo(heightPx: Int, content: @Composable () -> Unit): HTMLElement {
        val clippingContainer = document.createElement("div") as HTMLElement
        clippingContainer.style.cssText = "position: absolute; left: ${VIEWPORT_LEFT}px; top: ${VIEWPORT_TOP}px; width: 300px; height: ${heightPx}px; overflow: hidden;"
        val container = document.createElement("div") as HTMLElement
        container.style.cssText = "width: 300px; height: 200px;"
        clippingContainer.appendChild(container)
        requireNotNull(document.body).appendChild(clippingContainer)
        containers += clippingContainer
        ComposeViewport(container, configure = {}, content = content)
        return clippingContainer
    }

    /** Captures the viewport in [container] once Compose has written the node tagged [testTag] into its accessibility DOM. */
    private suspend fun captureOnceTaggedNodeWritten(container: HTMLElement, testTag: String): ComposeRoot {
        val shadowRoot = awaitShadowRoot(container)
        awaitCondition(shadowRoot) { shadowRoot.querySelector("#$testTag") != null }
        return viewportSource().capture(NodeTreeCaptureOptions()) ?: fail("the viewport reported no root")
    }

    /** The shadow root of the viewport in [container], once `ComposeViewport` has built it after Skia loads. */
    private suspend fun awaitShadowRoot(container: HTMLElement): ShadowRoot {
        awaitCondition(container) { container.viewportShadowRoot() != null }
        return container.viewportShadowRoot() ?: fail("the viewport has no shadow root")
    }

    private fun viewportSource(): ComposeNodeSource = ComposeNodeSourceRegistry.sources.single()

    private suspend fun performNodeAction(nodeId: Int, action: NodeAction): NodeActionResult = viewportSource().performAction(PerformNodeAction(rootId = viewportSource().sourceId, nodeId = nodeId, action = action))

    /** The highlight boxes over the viewport in [container], told from Compose's own elements by the z-index they are drawn above everything with. */
    private fun highlightBoxesIn(container: HTMLElement): List<HTMLElement> = container.viewportShadowRoot()
        ?.querySelectorAll("div")?.asList().orEmpty()
        .mapNotNull { it as? HTMLElement }
        .filter { it.style.zIndex == "2147483647" }
}

private const val VIEWPORT_LEFT = 30f
private const val VIEWPORT_TOP = 50f

private fun ComposeRoot?.nodeWithTag(testTag: String): ComposeNode = this?.allNodes().orEmpty().filterIsInstance<ComposeNode>().firstOrNull { it.testTag == testTag } ?: fail("no node tagged $testTag in $this")

private fun ComposeRoot.allNodes(): List<UiNode> = node?.withDescendants().orEmpty()

private fun UiNode.withDescendants(): List<UiNode> = listOf(this) + children.flatMap(UiNode::withDescendants)

@Composable
private fun Counter() {
    var count by remember { mutableIntStateOf(0) }
    BasicText("Count $count", Modifier.testTag("counter").clickable { count++ })
}

/** Runs [mutate] and suspends until a mutation observer on [target] has been told of what it changed. */
@OptIn(ExperimentalWasmJsInterop::class)
private suspend fun awaitMutation(target: Node, mutate: () -> Unit) {
    suspendCancellableCoroutine { continuation ->
        val observer = MutationObserver { _, observer ->
            observer.disconnect()
            continuation.resume(Unit)
        }
        observer.observe(target, subtreeMutationObserverInit(includeAttributeAndTextChanges = false))
        continuation.invokeOnCancellation { observer.disconnect() }
        mutate()
    }
}

/** Suspends until [condition] holds, checking it again on every change under [target]. */
@OptIn(ExperimentalWasmJsInterop::class)
private suspend fun awaitCondition(target: Node, condition: () -> Boolean) {
    if (condition()) return
    suspendCancellableCoroutine { continuation ->
        val observer = MutationObserver { _, observer ->
            if (condition()) {
                observer.disconnect()
                continuation.resume(Unit)
            }
        }
        observer.observe(target, subtreeMutationObserverInit(includeAttributeAndTextChanges = true))
        continuation.invokeOnCancellation { observer.disconnect() }
    }
}

/** The elements `ComposeViewport` builds that the probe recognizes a viewport by, with nothing of Compose's behind them. */
private fun viewportShadowHostWithoutCompose(): HTMLElement {
    val shadowHost = document.createElement("div") as HTMLElement
    val canvasContainer = document.createElement("div")
    canvasContainer.appendChild(document.createElement("canvas"))
    canvasContainer.appendChild(document.createElement("div").also { it.id = "cmp_a11y_root" })
    shadowHost.attachShadow(ShadowRootInit(ShadowRootMode.OPEN)).appendChild(canvasContainer)
    return shadowHost
}

private fun HTMLElement.viewportShadowRoot(): ShadowRoot? = querySelectorAll("div").asList().firstNotNullOfOrNull { (it as? Element)?.shadowRoot }
