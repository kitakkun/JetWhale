package com.kitakkun.jetwhale.plugins.semantics.protocol

import kotlinx.serialization.Serializable

/**
 * What to include when capturing the node tree.
 *
 * The captured tree is the Compose **semantics** tree: the tree that carries the labels, roles and
 * actions a node exposes, and therefore the one that says what is actually clickable. Nodes that
 * only lay out pixels (a `Box` with no semantics of its own) do not appear on their own.
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
 * A single Compose root — one composition backed by one platform view. A dialog or a popup gets its
 * own root, so a snapshot normally has more than one entry while a dialog is open.
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
    /** The root semantics node, or `null` when the root has no content yet. */
    val node: ComposeNode?,
)

/** One node of the Compose semantics tree. */
@Serializable
data class ComposeNode(
    /** Semantics id, unique within its root and stable while the node stays composed. */
    val id: Int,
    /** Semantics role (`Button`, `Checkbox`, `Tab`, ...), when the node declares one. */
    val role: String? = null,
    /** Concatenated `Text` semantics of the node. */
    val text: String? = null,
    /** Current content of an editable node (a text field). */
    val editableText: String? = null,
    val contentDescription: String? = null,
    /** `Modifier.testTag` value — the most reliable way to address a node from a test or an agent. */
    val testTag: String? = null,
    val stateDescription: String? = null,
    /** `On`, `Off` or `Indeterminate` for a toggleable node. */
    val toggleableState: String? = null,
    /** Bounds in this root's coordinate space, in pixels. */
    val bounds: NodeBounds,
    /**
     * Bounds in screen coordinates, in pixels — the ones to feed to `adb shell input tap`. Prefer
     * [PerformNodeAction] where possible: it invokes the node's own semantics action and does not
     * depend on the window still being where it was when the snapshot was taken.
     */
    val boundsInScreen: NodeBounds,
    /** Names of the semantics actions this node exposes, e.g. `OnClick`, `SetText`, `ScrollBy`. */
    val actions: List<String> = emptyList(),
    val isEnabled: Boolean = true,
    val isClickable: Boolean = false,
    val isFocused: Boolean = false,
    val isSelected: Boolean = false,
    val isEditable: Boolean = false,
    val isScrollable: Boolean = false,
    /** `false` when the node has empty bounds or is hidden from accessibility. */
    val isVisible: Boolean = true,
    val children: List<ComposeNode> = emptyList(),
)

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

/** A semantics action that [PerformNodeAction] can invoke on a node. */
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
