package com.kitakkun.jetwhale.plugins.semantics.agent

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewRootForTest
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Registers every Compose root in the process with [ComposeNodeSourceRegistry], from the
 * Application layer — no change to any screen.
 *
 * Call it from `Application.onCreate()`, before any activity exists: it hooks the callback Compose
 * fires when it creates the view backing a composition, so it sees **every** root, including the
 * separate ones a `Dialog` or a `Popup` gets. Installed later it also scans the resumed activity's
 * window, so roots created before the call are not lost — but roots in windows that were already
 * open and are never re-resumed can be.
 *
 * The install is process-wide and idempotent: calling it twice returns the same handle. Closing the
 * returned handle restores whatever callback was installed before, so it composes with the Compose
 * test framework rather than displacing it.
 *
 * @see JetWhaleSemanticsProbe for the in-composition alternative.
 */
fun installJetWhaleSemanticsProbe(application: Application): AutoCloseable = AndroidSemanticsProbe.install(application)

/**
 * Registers the Compose root that hosts this composition for as long as it stays composed.
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
 * It registers only **its own** root, and a `Dialog` or `Popup` composes into a root of its own —
 * so add a call inside those too, or install the Application-level probe, which finds them all.
 *
 * Safe to combine with [installJetWhaleSemanticsProbe]: registrations are reference counted per
 * root, so neither install can pull the root out from under the other.
 */
@Composable
fun JetWhaleSemanticsProbe() {
    val view = LocalView.current
    DisposableEffect(view) {
        val registration = (view as? ViewRootForTest)?.let { root ->
            ComposeNodeSourceRegistry.register(root.toNodeSource())
        }
        onDispose { registration?.close() }
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

    // Tracking is per view, not per install: the in-composition probe and the Application-level one
    // must not each attach their own attach-state listener to the same root.
    private val trackedViews = WeakHashMap<View, ViewTracker>()

    fun track(root: ViewRootForTest) = synchronized(lock) {
        trackedViews.getOrPut(root.view) {
            ViewTracker(root).also { tracker ->
                root.view.addOnAttachStateChangeListener(tracker)
                // A root discovered by scanning is normally attached already, so its listener would
                // never fire; register it here instead of waiting for a re-attach that never comes.
                if (root.view.isAttachedToWindow) tracker.onViewAttachedToWindow(root.view)
            }
        }
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
 * Holds a root's registration in step with its view being attached.
 *
 * A detached root has nothing to report, and its activity may be on its way out, so it leaves the
 * registry — but the tracker stays on the view so a re-attached one (a view pager page coming back)
 * registers again instead of disappearing for good.
 */
private class ViewTracker(root: ViewRootForTest) : View.OnAttachStateChangeListener {
    private val source = root.toNodeSource()

    // Weak so a tracker left on a view cannot keep it alive; held only so teardown can detach the
    // listener from the very view it was added to.
    private val viewRef = WeakReference(root.view)

    @Volatile
    private var registration: ComposeNodeSourceRegistry.Registration? = null

    override fun onViewAttachedToWindow(v: View) {
        if (registration == null) registration = ComposeNodeSourceRegistry.register(source)
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

/**
 * Wraps a root as a node source.
 *
 * The root is held weakly and re-checked per call: the registry outlives any single screen, and a
 * strong reference here would keep a destroyed activity's whole view tree alive for as long as the
 * process runs.
 */
private fun ViewRootForTest.toNodeSource(): SemanticsOwnerNodeSource {
    val rootRef = WeakReference(this)

    // A detached root has nothing readable to report, and reading its semantics can throw, so the
    // attachment check gates every lookup rather than only the registration.
    fun attached(): ViewRootForTest? = rootRef.get()?.takeIf { it.view.isAttachedToWindow }
    return SemanticsOwnerNodeSource(
        sourceId = "compose-root-${System.identityHashCode(view).toString(16)}",
        owner = { attached()?.semanticsOwner },
        label = { rootRef.get()?.view?.describeComposeRoot() ?: "(detached)" },
        density = { rootRef.get()?.density?.density ?: 1f },
        windowOffset = { rootRef.get()?.view?.windowOffsetOnScreen() ?: Offset.Zero },
        uiThread = AndroidComposeUiThread,
    )
}

/**
 * Names the root by the activity it belongs to, and by its window when that is not the activity's
 * own — which is how a dialog's or a popup's separate root tells itself apart in the host's list.
 */
private fun View.describeComposeRoot(): String {
    val activity = context.findActivity()
    val activityName = activity?.javaClass?.simpleName ?: context.javaClass.simpleName
    return if (activity?.window?.peekDecorView() === rootView) {
        activityName
    } else {
        "$activityName / ${rootView.javaClass.simpleName}"
    }
}

/**
 * Distance between this view's window and the screen, so window-relative semantics bounds can be
 * reported in screen coordinates — the ones `adb shell input tap` takes.
 */
private fun View.windowOffsetOnScreen(): Offset {
    val onScreen = IntArray(2).also(::getLocationOnScreen)
    val inWindow = IntArray(2).also(::getLocationInWindow)
    return Offset((onScreen[0] - inWindow[0]).toFloat(), (onScreen[1] - inWindow[1]).toFloat())
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
