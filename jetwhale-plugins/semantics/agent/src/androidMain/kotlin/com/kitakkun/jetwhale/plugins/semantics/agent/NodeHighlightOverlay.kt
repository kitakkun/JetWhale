package com.kitakkun.jetwhale.plugins.semantics.agent

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import java.lang.ref.WeakReference
import kotlin.time.Duration

/**
 * The box the host draws over one node, as a **window overlay**.
 *
 * `View.getOverlay()` is drawn by the host view after its children but is not in its child list, so
 * the plugin's own `for (index in 0 until childCount)` walk in `ViewTreeCapture` cannot see it — the
 * highlight never appears in the tree it is highlighting. A child view would, and would change the
 * very structure a user is reading.
 *
 * It goes on the window's root view, which sits at (0,0) in its window, so the window-relative bounds
 * every node already reports are the overlay's coordinate space and need no conversion. A dialog is
 * its own window with its own root, and gets its own overlay through its own source.
 *
 * One overlay per root: showing another node moves the existing drawable rather than stacking a
 * second one. Everything here must be called on the main thread.
 *
 * The invariant it holds is that a box on screen is always in the right place. Bounds are resolved
 * when the highlight is asked for, and anything that moves the node afterwards — a scroll, a
 * relayout, a rotation an activity handles itself rather than being recreated for — would otherwise
 * leave the box behind, so the node is re-resolved as the window redraws and the highlight is taken
 * down as soon as the node cannot be found any more.
 */
internal class NodeHighlightOverlay {
    private val mainHandler = Handler(Looper.getMainLooper())

    // The root view is held weakly: an overlay that is up keeps nothing alive, so a screen that goes
    // away between the show and the TTL costs nothing. A collected root has no overlay left to clear.
    private var attachedRootRef: WeakReference<View>? = null
    private var drawable: NodeHighlightDrawable? = null

    // Re-read rather than remembered, so a node that moves is followed instead of frozen where it
    // was when the host asked for it.
    private var resolveBounds: (() -> Rect?)? = null

    private val expire = Runnable { clear() }

    private var reResolvePosted = false
    private val reResolve = Runnable {
        reResolvePosted = false
        moveToCurrentBounds()
    }

    /**
     * A pre-draw hook rather than a layout one: a Compose list scrolling under the box lays nothing
     * out — it only draws — so a layout listener would watch the box drift. The work itself is a
     * posted, throttled re-resolve, so a frame here costs one boolean.
     */
    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (!reResolvePosted) {
            reResolvePosted = true
            mainHandler.postDelayed(reResolve, RE_RESOLVE_THROTTLE_MILLIS)
        }
        true
    }

    // A window that goes away while a highlight is up would otherwise keep the drawable registered on
    // a detached root, and the next show would find state that no longer matches the screen.
    private val detachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = Unit
        override fun onViewDetachedFromWindow(view: View) = clear()
    }

    /**
     * Draws the box over the node [resolveBounds] reports — window pixels, `null` once the node is
     * gone — on [rootView], replacing whatever was showing, and schedules it to clear itself after
     * [ttl].
     */
    fun show(rootView: View, resolveBounds: () -> Rect?, ttl: Duration) {
        val current = attachedRootRef?.get()
        if (current !== rootView) {
            clear()
            val created = NodeHighlightDrawable(strokeWidthPx = BORDER_WIDTH_DP * rootView.resources.displayMetrics.density)
            rootView.overlay.add(created)
            rootView.addOnAttachStateChangeListener(detachListener)
            rootView.viewTreeObserver.addOnPreDrawListener(preDrawListener)
            attachedRootRef = WeakReference(rootView)
            drawable = created
        }
        this.resolveBounds = resolveBounds
        moveToCurrentBounds()

        mainHandler.removeCallbacks(expire)
        val ttlMs = ttl.inWholeMilliseconds
        if (ttlMs > 0) mainHandler.postDelayed(expire, ttlMs)
    }

    /** [clear], from whichever thread the caller is on. */
    fun clearFromAnyThread() {
        if (Looper.myLooper() == Looper.getMainLooper()) clear() else mainHandler.post { clear() }
    }

    /** Takes the box down and stops watching the window. Doing this with nothing showing is a no-op. */
    fun clear() {
        mainHandler.removeCallbacks(expire)
        mainHandler.removeCallbacks(reResolve)
        reResolvePosted = false
        val rootView = attachedRootRef?.get()
        val current = drawable
        if (rootView != null && current != null) {
            rootView.overlay.remove(current)
            rootView.removeOnAttachStateChangeListener(detachListener)
            // A dead observer belongs to a window that is gone; there is nothing left to unregister
            // from, and asking it to remove would throw.
            rootView.viewTreeObserver.takeIf { it.isAlive }?.removeOnPreDrawListener(preDrawListener)
            rootView.invalidate()
        }
        attachedRootRef = null
        drawable = null
        resolveBounds = null
    }

    /**
     * Puts the box where the node is now, or takes it down when the node can no longer be found —
     * a box in the wrong place says something false, whereas no box says nothing.
     */
    private fun moveToCurrentBounds() {
        val rootView = attachedRootRef?.get() ?: return
        val current = drawable ?: return
        // Runs from a posted callback with nothing above it to catch: a composition disposed between
        // frames can make the lookup throw, and an exception here would take the app down. A node
        // that cannot be resolved is a node that is gone.
        val bounds = try {
            resolveBounds?.invoke()
        } catch (_: Throwable) {
            null
        }
        if (bounds == null || bounds.isEmpty) {
            clear()
            return
        }
        // Only when it actually moved: the invalidate below causes the next frame, whose pre-draw
        // would otherwise schedule the next re-resolve, and so on for as long as the box is up.
        if (current.bounds != bounds) {
            current.bounds = bounds
            rootView.invalidate()
        }
    }
}

/**
 * How long a run of frames is coalesced into one re-resolve.
 *
 * A Compose node is found by walking the semantics tree, which is too much to do per frame during an
 * animation; a tenth of a second is short enough that the box is not visibly behind the content it
 * is drawn over.
 */
private const val RE_RESOLVE_THROTTLE_MILLIS = 100L

/**
 * A translucent fill with a solid border.
 *
 * One style only: hover and selection are never shown at the same time, so a second colour would
 * carry no information. The magenta is hard-coded because it has to read against whatever the app
 * happens to be drawing — it is far enough from the greys, blues and whites of ordinary app chrome to
 * stay visible on both light and dark content.
 */
private class NodeHighlightDrawable(private val strokeWidthPx: Float) : Drawable() {
    private val fillPaint = Paint().apply {
        style = Paint.Style.FILL
        color = FILL_COLOR
        isAntiAlias = true
    }
    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
        color = BORDER_COLOR
        strokeWidth = strokeWidthPx
        isAntiAlias = true
    }

    override fun draw(canvas: Canvas) {
        val box = bounds
        canvas.drawRect(box, fillPaint)
        // A stroke straddles the line it is drawn on, so it is inset by half its width to sit inside
        // the node's bounds instead of spilling a pixel over the neighbours.
        val inset = strokeWidthPx / 2f
        canvas.drawRect(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset, borderPaint)
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Drawable, but still abstract, so it has to be implemented.")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

private const val BORDER_WIDTH_DP = 2f

private val BORDER_COLOR = Color.argb(0xFF, 0xFF, 0x3D, 0x71)

/** Light enough to read the highlighted content through, solid enough to find at a glance. */
private val FILL_COLOR = Color.argb(0x40, 0xFF, 0x3D, 0x71)
