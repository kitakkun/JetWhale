package com.kitakkun.jetwhale.plugins.semantics.agent

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewRootForTest
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Registers every window that hosts a Compose root with [ComposeNodeSourceRegistry], from the
 * Application layer — no change to any screen.
 *
 * Call it from `Application.onCreate()`, before any activity exists: it hooks the callback Compose
 * fires when it creates the view backing a composition, so it sees **every** window that holds one,
 * including the separate ones a `Dialog` or a `Popup` opens. Installed later it also scans the
 * resumed activity's window, so roots created before the call are not lost — but roots in windows
 * that were already open and are never re-resumed can be.
 *
 * A window with no Compose in it at all is never registered: the composition is what announces it.
 *
 * The install is process-wide and idempotent: calling it twice returns the same handle. Closing the
 * returned handle restores whatever callback was installed before, so it composes with the Compose
 * test framework rather than displacing it.
 *
 * @see JetWhaleSemanticsProbe for the in-composition alternative.
 */
fun installJetWhaleSemanticsProbe(application: Application): AutoCloseable = AndroidSemanticsProbe.install(application)

/**
 * Registers the window that hosts this composition for as long as it stays composed.
 *
 * Use it when the Application layer is not yours to touch, or when only one screen should be
 * readable. Call it once inside `setContent { … }`:
 *
 * ```kotlin
 * setContent {
 *     JetWhaleSemanticsProbe()
 *     App()
 * }
 * ```
 *
 * It registers only the window it is called in, and a `Dialog` or `Popup` opens a window of its own
 * — so add a call inside those too, or install the Application-level probe, which finds them all.
 *
 * Safe to combine with [installJetWhaleSemanticsProbe]: registrations are reference counted per
 * window, so neither install can pull a window out from under the other.
 */
@Composable
fun JetWhaleSemanticsProbe() {
    val view = LocalView.current
    DisposableEffect(view) {
        val tracker = WindowTracker(view)
        onDispose { tracker.dispose() }
    }
}

private object AndroidSemanticsProbe {
    private val lock = Any()
    private var installation: Installation? = null

    fun install(application: Application): AutoCloseable = synchronized(lock) {
        installation ?: Installation(application).also { installation = it }
    }

    fun uninstalled(installation: Installation) = synchronized(lock) {
        if (this.installation === installation) this.installation = null
    }

    // One tracker per Compose root, however often that root is discovered: the created-callback and
    // the activity scan both see the same root, and each must not add its own attach-state listener.
    private val trackedViews = WeakHashMap<View, WindowTracker>()

    fun track(root: ViewRootForTest) = synchronized(lock) {
        trackedViews.getOrPut(root.view) { WindowTracker(root.view) }
        Unit
    }

    fun untrackAll() = synchronized(lock) {
        trackedViews.values.toList().forEach { it.dispose() }
        trackedViews.clear()
    }

    class Installation(private val application: Application) : AutoCloseable {
        private val previousCallback = ViewRootForTest.onViewCreatedCallback

        private val viewCreatedCallback: (ViewRootForTest) -> Unit = { root ->
            // Chained rather than replaced: the Compose test framework uses this same slot, and a
            // debug build may well be running both.
            previousCallback?.invoke(root)
            AndroidSemanticsProbe.track(root)
        }

        private val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                activity.window?.peekDecorView()?.let(::scanForComposeRoots)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }

        init {
            ViewRootForTest.onViewCreatedCallback = viewCreatedCallback
            application.registerActivityLifecycleCallbacks(activityCallbacks)
        }

        override fun close() {
            // Only restore when ours is still the installed one: something installed afterwards
            // owns the slot now, and overwriting it would silently disable that.
            if (ViewRootForTest.onViewCreatedCallback === viewCreatedCallback) {
                ViewRootForTest.onViewCreatedCallback = previousCallback
            }
            application.unregisterActivityLifecycleCallbacks(activityCallbacks)
            AndroidSemanticsProbe.untrackAll()
            AndroidSemanticsProbe.uninstalled(this)
        }
    }
}

/** Depth-first walk for roots that already existed when the probe was installed. */
private fun scanForComposeRoots(view: View) {
    if (view is ViewRootForTest) {
        AndroidSemanticsProbe.track(view)
        return
    }
    if (view is ViewGroup) {
        for (index in 0 until view.childCount) scanForComposeRoots(view.getChildAt(index))
    }
}

/**
 * Keeps the window around a Compose root registered for as long as that root is attached.
 *
 * What gets registered is the **window**, not the composition: a window is what a user sees, and its
 * layout is as much part of the picture as the composition inside it. A view only belongs to a
 * window once it is attached — `View.rootView` is its own view until then — so the source is built
 * on attach rather than up front.
 *
 * A detached root has nothing to report, and its activity may be on its way out, so it leaves the
 * registry — but the tracker stays on the view so a re-attached one (a view pager page coming back)
 * registers again instead of disappearing for good. Several Compose roots in one window each hold
 * their own claim on it, and the registry counts them, so the window stays registered until the last
 * of them goes.
 */
private class WindowTracker(view: View) : View.OnAttachStateChangeListener {
    // Weak so a tracker left on a view cannot keep it alive; held only so teardown can detach the
    // listener from the very view it was added to.
    private val viewRef = WeakReference(view)

    @Volatile
    private var registration: ComposeNodeSourceRegistry.Registration? = null

    init {
        view.addOnAttachStateChangeListener(this)
        // A root discovered by scanning, or one already composed when the probe was called, is
        // normally attached already, so its listener would never fire; register it here instead of
        // waiting for a re-attach that never comes.
        if (view.isAttachedToWindow) onViewAttachedToWindow(view)
    }

    override fun onViewAttachedToWindow(v: View) {
        if (registration == null) registration = ComposeNodeSourceRegistry.register(AndroidWindowNodeSource(v.rootView))
    }

    override fun onViewDetachedFromWindow(v: View) {
        registration?.close()
        registration = null
    }

    fun dispose() {
        registration?.close()
        registration = null
        // Detaching matters as much as closing the registration: a listener left behind would
        // re-register the root on the next attach, after the probe was uninstalled.
        viewRef.get()?.removeOnAttachStateChangeListener(this)
    }
}
