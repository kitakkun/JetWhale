package com.kitakkun.jetwhale.plugins.semantics.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What to include when capturing the node tree.
 *
 * The captured tree is the Compose **semantics** tree: the tree that carries the labels, roles and
 * actions a node exposes, and therefore the one that says what is actually clickable. Nodes that
 * only lay out pixels (a `Box` with no semantics of its own) do not appear on their own.
 *
 * On Android the tree also carries the Android `View`s around and inside the composition — the
 * layout hosting a `ComposeView` and the content of an `AndroidView { }` — as [ViewNode]s. These
 * options apply to them identically: depth counts every node whatever its type, and a `View` with
 * empty bounds or `visibility == GONE` is invisible rather than absent.
 */
@Serializable
data class NodeTreeCaptureOptions(
    /**
     * `true` captures the merged tree — the one accessibility services see, where a `Button`'s
     * label is folded into the clickable node. `false` captures the unmerged tree, which keeps
     * every semantics node separate (closer to how the UI is written).
     */
    val merged: Boolean = true,
    /** Include nodes whose bounds are empty (not laid out, or fully clipped away). */
    val includeInvisible: Boolean = false,
    /** Stop descending past this depth (the root is depth 0). `null` captures the whole tree. */
    val maxDepth: Int? = null,
)

/** One capture of every Compose root known to the agent. */
@Serializable
data class NodeTreeSnapshot(
    /** When the capture was taken, in epoch milliseconds on the device. */
    val capturedAtMs: Long,
    /** How long the capture itself took on the device, in milliseconds. */
    val captureDurationMs: Long,
    /** Echoes the options the capture ran with, so a consumer can tell merged from unmerged. */
    val options: NodeTreeCaptureOptions,
    /** One entry per Compose root (window), in registration order — the newest window is last. */
    val roots: List<ComposeRoot>,
    /**
     * Roots that could not be captured, e.g. because their view was detached mid-capture. Reported
     * rather than thrown: a partial tree is still useful.
     */
    val warnings: List<String> = emptyList(),
)

/**
 * A single root — one platform window and everything the agent can read inside it. A dialog or a
 * popup gets its own root, so a snapshot normally has more than one entry while a dialog is open.
 *
 * On Android a root is the window as a whole: its node tree starts at the window's decor view and
 * descends through the Android `View` hierarchy into every composition it hosts. Elsewhere a root is
 * one composition, and its tree is the semantics tree alone.
 */
@Serializable
data class ComposeRoot(
    /** Stable while the root stays attached; address nodes with it in [PerformNodeAction]. */
    val rootId: String,
    /** Human-readable origin, e.g. `MainActivity` or `PopupWindow`. */
    val label: String,
    /** Device density (px per dp) of this root, for converting the pixel bounds below to dp. */
    val density: Float,
    /**
     * Where this root's window sits on screen, in pixels. Node bounds are reported in both root and
     * screen coordinates, so this is only needed to reason about the window itself.
     */
    val windowOffsetX: Float,
    val windowOffsetY: Float,
    /** The root node, or `null` when the root has no content yet. */
    val node: UiNode?,
)

/**
 * One node of the captured tree: a Compose semantics node ([ComposeNode]), or on Android an
 * interoperating `View` ([ViewNode]).
 *
 * Everything declared here a node of either type answers, so a consumer that only reads the tree —
 * searching it, drawing it, tapping its bounds — never has to know which type it holds.
 */
@Serializable
sealed interface UiNode {
    /**
     * Addresses the node within its root and stays valid while the node is on screen. A
     * [ComposeNode] reports its semantics id, which is non-negative; a [ViewNode] reports a negative
     * id assigned by the agent, so the two can never collide.
     */
    val id: Int

    /** Concatenated `Text` semantics of the node, or a `TextView`'s label. */
    val text: String?

    /** Current content of an editable node (a text field). */
    val editableText: String?

    val contentDescription: String?

    /** `On`, `Off` or `Indeterminate` for a toggleable node, or for a `Checkable` `View`. */
    val toggleableState: String?

    /** Bounds in this root's coordinate space, in pixels. */
    val bounds: NodeBounds

    /**
     * Bounds in screen coordinates, in pixels — the ones to feed to `adb shell input tap`. Prefer
     * [PerformNodeAction] where possible: it invokes the node's own action and does not depend on
     * the window still being where it was when the snapshot was taken.
     */
    val boundsInScreen: NodeBounds

    /**
     * Names of the actions this node exposes, e.g. `OnClick`, `SetText`, `ScrollBy`. A [ViewNode]
     * advertises what its own class can do under the same names, so a caller picks an action the
     * same way whichever type the node is.
     */
    val actions: List<String>

    val isEnabled: Boolean
    val isClickable: Boolean
    val isFocused: Boolean
    val isSelected: Boolean
    val isEditable: Boolean
    val isScrollable: Boolean

    /**
     * `false` when the node has empty bounds or is hidden from accessibility — for a [ViewNode],
     * also when its `visibility` is not `VISIBLE` or an ancestor hides it.
     */
    val isVisible: Boolean

    /** Children of either type: an Android tree crosses between the two wherever the real UI does. */
    val children: List<UiNode>
}

/**
 * A node of the Compose semantics tree.
 *
 * The optional properties default to the unremarkable state — enabled, visible, unfocused, no label —
 * so a node only has to spell out what sets it apart.
 */
@Serializable
@SerialName("compose")
data class ComposeNode(
    override val id: Int,
    /** Semantics role (`Button`, `Checkbox`, `Tab`, ...), when the node declares one. */
    val role: String? = null,
    override val text: String? = null,
    override val editableText: String? = null,
    override val contentDescription: String? = null,
    /** `Modifier.testTag` value — the most reliable way to address a node from a test or an agent. */
    val testTag: String? = null,
    val stateDescription: String? = null,
    override val toggleableState: String? = null,
    override val bounds: NodeBounds,
    override val boundsInScreen: NodeBounds,
    override val actions: List<String> = emptyList(),
    override val isEnabled: Boolean = true,
    override val isClickable: Boolean = false,
    override val isFocused: Boolean = false,
    override val isSelected: Boolean = false,
    override val isEditable: Boolean = false,
    override val isScrollable: Boolean = false,
    override val isVisible: Boolean = true,
    override val children: List<UiNode> = emptyList(),
) : UiNode

/**
 * An Android `View`, either around a composition or embedded in one by `AndroidView { }`.
 *
 * Optional properties default to the unremarkable state, as on [ComposeNode].
 */
@Serializable
@SerialName("view")
data class ViewNode(
    /** Assigned by the agent and negative, so it cannot collide with a [ComposeNode]'s id. */
    override val id: Int,
    /** Fully-qualified class name, e.g. `android.widget.TextView` — what says the thing is. */
    val viewClass: String,
    /**
     * Entry name of the view's `android:id`, e.g. `submit` for `@id/submit`. A `View` has no
     * `testTag`, and this is what plays that role.
     */
    val resourceId: String? = null,
    override val text: String? = null,
    override val editableText: String? = null,
    override val contentDescription: String? = null,
    override val toggleableState: String? = null,
    override val bounds: NodeBounds,
    override val boundsInScreen: NodeBounds,
    override val actions: List<String> = emptyList(),
    override val isEnabled: Boolean = true,
    override val isClickable: Boolean = false,
    override val isFocused: Boolean = false,
    override val isSelected: Boolean = false,
    override val isEditable: Boolean = false,
    override val isScrollable: Boolean = false,
    override val isVisible: Boolean = true,
    override val children: List<UiNode> = emptyList(),
) : UiNode

/** A rectangle in pixels. */
@Serializable
data class NodeBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    /** `true` when the rectangle encloses no pixels, i.e. the node is not laid out or fully clipped. */
    val isEmpty: Boolean get() = width <= 0f || height <= 0f
}

/**
 * An action that [PerformNodeAction] can invoke on a node.
 *
 * On a [ComposeNode] it is the node's own semantics action. On a [ViewNode] it is the closest
 * equivalent the platform offers — [Click] calls `performClick()`, [SetText] sets an `EditText`'s
 * content — and [Dismiss], [Expand] and [Collapse] have none, so they report that they did not run.
 */
@Serializable
enum class NodeAction {
    /** Invokes `SemanticsActions.OnClick` — what a tap on the node would do. */
    Click,

    /** Invokes `SemanticsActions.OnLongClick`. */
    LongClick,

    /** Replaces an editable node's content via `SemanticsActions.SetText`. Requires `text`. */
    SetText,

    /** Appends to an editable node's content via `SemanticsActions.InsertTextAtCursor`. Requires `text`. */
    InsertText,

    /** Submits an editable node via `SemanticsActions.OnImeAction`. */
    ImeAction,

    /** Scrolls a scrollable node by `scrollX`/`scrollY` pixels via `SemanticsActions.ScrollBy`. */
    ScrollBy,

    /** Gives the node keyboard focus via `SemanticsActions.RequestFocus`. */
    RequestFocus,

    /** Dismisses the node (a dialog, a snackbar) via `SemanticsActions.Dismiss`. */
    Dismiss,

    Expand,
    Collapse,
}

/** Outcome of a [PerformNodeAction]. */
@Serializable
data class NodeActionResult(
    /** `true` only when the node's action ran and reported success. */
    val performed: Boolean,
    /** Why the action did not run, or a note about what did run. */
    val message: String? = null,
)
