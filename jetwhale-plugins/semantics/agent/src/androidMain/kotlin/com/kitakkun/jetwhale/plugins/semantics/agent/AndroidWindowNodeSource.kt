package com.kitakkun.jetwhale.plugins.semantics.agent

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getAllSemanticsNodes
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeRoot
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import java.lang.ref.WeakReference

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
internal class AndroidWindowNodeSource(rootView: View) : ComposeNodeSource {
    override val sourceId: String = "android-window-${System.identityHashCode(rootView).toString(16)}"

    private val rootViewRef = WeakReference(rootView)

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

        // The sign of the id says which half of the tree the node came from: Compose's semantics ids
        // are non-negative, the ones this agent assigns to views are negative.
        if (request.nodeId < 0) {
            val view = ViewNodeIds.viewOf(request.nodeId)?.takeIf { it.rootView === rootView }
                ?: return@await unknownNode(request.nodeId)
            view.performViewAction(request)
        } else {
            val node = rootView.findSemanticsNode(request.nodeId) ?: return@await unknownNode(request.nodeId)
            node.performSemanticsAction(request)
        }
    }

    private fun unknownNode(nodeId: Int): NodeActionResult = NodeActionResult(
        performed = false,
        message = "unknown nodeId: $nodeId (the node may have left this window; capture the tree again)",
    )
}

/**
 * Searches every composition in the window for a semantics node.
 *
 * A node id addresses the same layout node in both trees, but a node merged into its parent is only
 * present in the unmerged one — so a lookup that missed in the merged tree still has somewhere to
 * look. The merged tree comes first because its config carries the actions a caller saw advertised.
 */
private fun View.findSemanticsNode(id: Int): SemanticsNode? = composeRootsInWindow().firstNotNullOfOrNull { root ->
    val owner = root.semanticsOwner
    owner.getAllSemanticsNodes(mergingEnabled = true, skipDeactivatedNodes = true).firstOrNull { it.id == id }
        ?: owner.getAllSemanticsNodes(mergingEnabled = false, skipDeactivatedNodes = true).firstOrNull { it.id == id }
}

/**
 * Every composition in this view's subtree, outermost first. A composition's own children are not
 * descended into as views: a nested `ComposeView` inside an `AndroidView { }` is reached through the
 * interop view Compose reports for it, not by walking Compose's internal scaffolding.
 */
private fun View.composeRootsInWindow(): Sequence<ViewRootForTest> = sequence {
    if (this@composeRootsInWindow is ViewRootForTest) {
        yield(this@composeRootsInWindow)
        return@sequence
    }
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
