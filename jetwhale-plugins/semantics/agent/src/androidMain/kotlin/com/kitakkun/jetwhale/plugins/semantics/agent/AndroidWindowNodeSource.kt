package com.kitakkun.jetwhale.plugins.semantics.agent

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getAllSemanticsNodes
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeRoot
import com.kitakkun.jetwhale.plugins.semantics.protocol.HighlightResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeSnapshot
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeValue
import java.lang.ref.WeakReference
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.time.Duration

/**
 * Reads one Android **window** — its `View` hierarchy and every composition inside it — as a single
 * root.
 *
 * A window is the unit a user sees: a Compose screen normally sits in a layout that is itself part
 * of the picture, and an `AndroidView { }` puts views back inside the composition. Reporting one
 * root per composition would cut that in two and lose which contains which, so the walk starts at
 * the window's root view and crosses between the two worlds wherever the real UI does. A `Dialog` or
 * a `Popup` is a window of its own and so gets a root of its own, as before.
 *
 * The window's root view is held weakly and re-checked per call: the registry outlives any single
 * screen, and a strong reference here would keep a destroyed activity's whole view tree alive for as
 * long as the process runs.
 */
internal class AndroidWindowNodeSource(rootView: View) :
    ComposeNodeSource,
    ViewAttributeSource,
    NodeHighlightSource {
    override val sourceId: String = "android-window-${System.identityHashCode(rootView).toString(16)}"

    private val rootViewRef = WeakReference(rootView)

    // At most one box per window, so pointing at another node moves this one.
    private val highlightOverlay = NodeHighlightOverlay()

    // A detached window has nothing readable to report, and reading a composition inside it can
    // throw, so the attachment check gates every call rather than only the registration.
    private fun attachedRootView(): View? = rootViewRef.get()?.takeIf { it.isAttachedToWindow }

    override suspend fun capture(options: NodeTreeCaptureOptions): ComposeRoot? = AndroidComposeUiThread.await {
        val rootView = attachedRootView() ?: return@await null
        val offset = rootView.windowOffsetOnScreen()
        ComposeRoot(
            rootId = sourceId,
            label = rootView.describeWindow(),
            density = rootView.resources.displayMetrics.density,
            windowOffsetX = offset.x,
            windowOffsetY = offset.y,
            isTouchModal = rootView.isTouchModal(),
            node = rootView.toViewNode(
                options = options,
                windowOffsetX = offset.x,
                windowOffsetY = offset.y,
                depth = 0,
            ),
        )
    }

    override suspend fun performAction(request: PerformNodeAction): NodeActionResult = AndroidComposeUiThread.await {
        val rootView = attachedRootView()
            ?: return@await NodeActionResult(performed = false, message = "the window is no longer readable")

        if (request.nodeId < 0) {
            val view = viewInWindow(request.nodeId, rootView) ?: return@await unknownNode(request.nodeId)
            view.performViewAction(request)
        } else {
            val found = rootView.findSemanticsNode(request.nodeId) ?: return@await unknownNode(request.nodeId)
            found.node.performSemanticsAction(request, revealInHost = found.hostView::requestRectangleOnScreenNow)
        }
    }

    // -- ViewAttributeSource ---------------------------------------------------
    //
    // A window is the only root that has platform attributes at all: its nodes include real `View`s.
    // The work itself lives in ViewAttributes.kt; this only resolves the node and hops to the UI
    // thread, the same way performAction does.

    override suspend fun attributes(nodeId: Int): ViewAttributeSnapshot? = AndroidComposeUiThread.await {
        val rootView = attachedRootView() ?: return@await null
        viewInWindow(nodeId, rootView)?.readAttributes(rootId = sourceId, nodeId = nodeId)
    }

    override suspend fun setAttribute(nodeId: Int, attributeId: String, value: ViewAttributeValue): ViewAttributeResult = AndroidComposeUiThread.await {
        val rootView = attachedRootView()
            ?: return@await ViewAttributeResult(applied = false, message = "the window is no longer readable")
        val view = viewInWindow(nodeId, rootView)
            ?: return@await ViewAttributeResult(applied = false, message = noViewAttributesMessage(nodeId))
        view.writeAttribute(attributeId = attributeId, value = value)
    }

    // -- NodeHighlightSource ---------------------------------------------------
    //
    // A window is the only root that can be pointed at: it has a decor view to hang an overlay on.
    // The drawing lives in NodeHighlightOverlay.kt; this only resolves the node's bounds and hops to
    // the UI thread, the same way the other two capabilities do.

    override suspend fun highlight(nodeId: Int?, ttl: Duration): HighlightResult = AndroidComposeUiThread.await {
        if (nodeId == null) {
            highlightOverlay.clear()
            return@await HighlightResult(shown = false)
        }
        // A request that cannot be honored still replaces what was showing: the caller asked to point
        // at something else, and a box left on the previous node would answer a question nobody is
        // asking any more.
        val rootView = attachedRootView()
        if (rootView == null) {
            highlightOverlay.clear()
            return@await HighlightResult(shown = false, message = "the window is no longer readable")
        }
        // Passed as a lookup rather than as the bounds it currently reports: a scroll or a relayout
        // moves the node, and the overlay follows it by asking again.
        val resolveBounds = { rootView.highlightBoundsOf(nodeId) }
        val bounds = resolveBounds()
        if (bounds == null) {
            highlightOverlay.clear()
            return@await HighlightResult(
                shown = false,
                message = "unknown nodeId: $nodeId (the node may have left this window; capture the tree again)",
            )
        }
        if (bounds.isEmpty) {
            highlightOverlay.clear()
            return@await HighlightResult(
                shown = false,
                message = "node $nodeId has no area in this window (it is invisible, unmeasured, or fully clipped)",
            )
        }
        highlightOverlay.show(rootView = rootView, resolveBounds = resolveBounds, ttl = ttl)
        HighlightResult(shown = true)
    }

    private fun unknownNode(nodeId: Int): NodeActionResult = NodeActionResult(
        performed = false,
        message = "unknown nodeId: $nodeId (the node may have left this window; capture the tree again)",
    )
}

/**
 * The `View` [nodeId] names, when it is a `View` node that is still part of [rootView]'s window.
 *
 * The sign of the id says which half of the tree the node came from: Compose's semantics ids are
 * non-negative, the ones this agent assigns to views are negative — so a Compose id resolves to no
 * view here, which is the right answer for both callers.
 */
private fun viewInWindow(nodeId: Int, rootView: View): View? = ViewNodeIds.viewOf(nodeId)?.takeIf { it.rootView === rootView }

/**
 * A semantics node together with the view its composition is drawn into — the view whose
 * coordinate space the node's root coordinates are, and the one to ask when the window's own
 * `View`s have to scroll for the node.
 */
private class SemanticsNodeInWindow(val node: SemanticsNode, val hostView: View)

/**
 * Where the node [nodeId] names can be seen in this window, in pixels, or `null` when the window has
 * no such node. Empty when the node is there but has no visible area.
 *
 * Both halves of the tree already report their bounds in the window's space — a `View`'s
 * `getGlobalVisibleRect` (whose "global" is the window's root), a semantics node's `boundsInWindow`
 * — which is the same space the overlay on the window's root view draws in, so nothing has to be
 * converted. The visible rect rather than the laid-out one: the overlay hangs on the window's root,
 * above every `ScrollView` and clipping parent, so a box the size of the layout would be painted over
 * content the view itself is clipped away from.
 */
private fun View.highlightBoundsOf(nodeId: Int): Rect? = if (nodeId < 0) {
    viewInWindow(nodeId, this)?.let { view ->
        Rect().also { visible -> if (!view.getGlobalVisibleRect(visible)) visible.setEmpty() }
    }
} else {
    findSemanticsNode(nodeId)?.boundsInWindow?.let {
        Rect(it.left.roundToInt(), it.top.roundToInt(), it.right.roundToInt(), it.bottom.roundToInt())
    }
}

/**
 * Searches every composition in the window for a semantics node.
 *
 * A node id addresses the same layout node in both trees, but a node merged into its parent is only
 * present in the unmerged one — so a lookup that missed in the merged tree still has somewhere to
 * look. The merged tree comes first because its config carries the actions a caller saw advertised.
 */
private fun View.findSemanticsNode(id: Int): SemanticsNodeInWindow? = composeRootsInWindow().firstNotNullOfOrNull { root ->
    val owner = root.semanticsOwner
    val node = owner.getAllSemanticsNodes(mergingEnabled = true, skipDeactivatedNodes = true).firstOrNull { it.id == id }
        ?: owner.getAllSemanticsNodes(mergingEnabled = false, skipDeactivatedNodes = true).firstOrNull { it.id == id }
    node?.let { SemanticsNodeInWindow(node = it, hostView = root.view) }
}

/**
 * Scrolls the window's `View`s so that [bounds], in this view's own coordinates, is on screen —
 * `ScrollView`, `RecyclerView` and a Compose `AndroidView { }` holder all answer
 * `requestChildRectangleOnScreen`, so one call climbs the whole way up. Immediate rather than
 * animated, so the next capture already sees the result.
 */
private fun View.requestRectangleOnScreenNow(bounds: Rect): Boolean = requestRectangleOnScreen(bounds.toOutwardAndroidRect(), true)

/**
 * The smallest integer rectangle that covers [this] — a request to show it must not lose the
 * fraction of a pixel at either edge.
 */
private fun Rect.toOutwardAndroidRect(): android.graphics.Rect = android.graphics.Rect(
    floor(left).toInt(),
    floor(top).toInt(),
    ceil(right).toInt(),
    ceil(bottom).toInt(),
)

/**
 * Every composition in this view's subtree, outermost first. A composition's own children are not
 * descended into as views: a nested `ComposeView` inside an `AndroidView { }` is reached through the
 * interop view Compose reports for it, not by walking Compose's internal scaffolding.
 */
private fun View.composeRootsInWindow(): Sequence<ViewRootForTest> = sequence {
    if (this@composeRootsInWindow is ViewRootForTest) yield(this@composeRootsInWindow)
    // A Compose root's own children are the views an `AndroidView { }` embeds, and one of those can
    // host a further composition — so the descent continues through it rather than stopping at it.
    if (this@composeRootsInWindow is ViewGroup) {
        for (index in 0 until childCount) {
            getChildAt(index)?.let { yieldAll(it.composeRootsInWindow()) }
        }
    }
}

/**
 * Names the window by the activity it belongs to, and by its root view when that is not the
 * activity's own — which is how a dialog's or a popup's separate window tells itself apart in the
 * host's list.
 */
internal fun View.describeWindow(): String {
    // A DecorView's own context is the window's decor context, which does not wrap the activity;
    // the views inside it do, so the activity is looked up from the first Compose root instead.
    val activity = composeRootsInWindow().firstOrNull()?.view?.context?.findActivity() ?: context.findActivity()
    val activityName = activity?.javaClass?.simpleName ?: context.javaClass.simpleName
    return if (activity?.window?.peekDecorView() === this) {
        activityName
    } else {
        "$activityName / ${javaClass.simpleName}"
    }
}

/**
 * Whether this window takes the touches that land outside it — a dialog does, which is what puts
 * the window underneath it out of a finger's reach.
 *
 * A window root's layout params are the window's own; anything else is a view inside one, and a
 * view cannot claim its window's touches, so it reports `false`.
 */
internal fun View.isTouchModal(): Boolean {
    val params = layoutParams as? WindowManager.LayoutParams ?: return false
    return params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL == 0
}

/**
 * Distance between this view's window and the screen, so window-relative bounds can be reported in
 * screen coordinates — the ones `adb shell input tap` takes.
 */
internal fun View.windowOffsetOnScreen(): Offset {
    val onScreen = IntArray(2).also(::getLocationOnScreen)
    val inWindow = IntArray(2).also(::getLocationInWindow)
    return Offset((onScreen[0] - inWindow[0]).toFloat(), (onScreen[1] - inWindow[1]).toFloat())
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
