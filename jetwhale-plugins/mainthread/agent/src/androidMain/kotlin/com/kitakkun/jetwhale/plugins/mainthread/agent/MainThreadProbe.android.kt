package com.kitakkun.jetwhale.plugins.mainthread.agent

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.StrictMode
import android.util.Printer
import android.view.FrameMetrics
import android.view.Window
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MonitorCapabilities
import java.util.concurrent.Executors

internal actual fun createMainThreadProbe(recorder: MainThreadRecorder, labels: () -> String?): MainThreadProbe = AndroidMainThreadProbe(recorder, labels)

private const val LOOPER_DISPATCHING = ">>>>>"
private const val LOOPER_FINISHED = "<<<<<"

/**
 * Watches Android's main thread through the platform's own hooks, each put back as it was on [stop]:
 * the main Looper's message logging times every message, a sampler thread reads the main thread's
 * stack while one runs long, a StrictMode thread policy reports disk and network access on the main
 * thread, and FrameMetrics reports each frame of the resumed activities.
 */
private class AndroidMainThreadProbe(
    private val recorder: MainThreadRecorder,
    private val labels: () -> String?,
) : MainThreadProbe {
    private val mainLooper = Looper.getMainLooper()
    private val mainHandler = Handler(mainLooper)
    private val sampler = StackSampler(recorder) { mainLooper.thread }

    private var started = false
    private var previousPrinter: Printer? = null
    private var previousPolicy: StrictMode.ThreadPolicy? = null

    // Written on the main thread when the policy is installed, read by the host's report request.
    @Volatile private var appOwnsStrictMode = false

    private var frameThread: HandlerThread? = null
    private val frameWindows = mutableMapOf<Window, Window.OnFrameMetricsAvailableListener>()
    private var application: Application? = null

    override val capabilities: MonitorCapabilities
        get() = MonitorCapabilities(
            platform = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            taskTiming = true,
            stackSampling = true,
            strictMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !appOwnsStrictMode,
            frameTiming = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N,
            note = when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.P -> "StrictMode violations need Android 9 (API 28) or later."
                appOwnsStrictMode -> "The app sets its own StrictMode thread policy, so it is left alone and its violations are not collected here."
                else -> null
            },
        )

    override fun start() {
        if (started) return
        started = true
        previousPrinter = currentLooperPrinter()
        mainLooper.setMessageLogging(::onLooperLine)
        sampler.start()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) mainHandler.post(::installStrictMode)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) startFrameTiming()
    }

    override fun stop() {
        if (!started) return
        started = false
        mainLooper.setMessageLogging(previousPrinter)
        sampler.stop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) mainHandler.post(::restoreStrictMode)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopFrameTiming()
    }

    // Called for every message the main thread handles, so it does no more than a prefix check.
    private fun onLooperLine(line: String) {
        previousPrinter?.println(line)
        when {
            line.startsWith(LOOPER_DISPATCHING) -> recorder.taskStarted(line, labels())
            line.startsWith(LOOPER_FINISHED) -> recorder.taskFinished()
        }
    }

    // A thread policy is per thread, so this runs on the main thread.
    private fun installStrictMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val previous = StrictMode.getThreadPolicy()
        // Detecting more on top of an app's own policy would apply its penalties — penaltyDeath
        // included — to violations it never asked about, so an app with a policy keeps it untouched.
        // Every app starts with the platform's policy instead, which only makes network access on
        // the main thread throw; building on it keeps that behavior as it was.
        if (previous.toString() !in platformDefaultPolicies) {
            appOwnsStrictMode = true
            return
        }
        previousPolicy = previous
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder(previous)
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .detectResourceMismatches()
                .detectUnbufferedIo()
                .penaltyListener(Executors.newSingleThreadExecutor()) { violation ->
                    recorder.violation(
                        kind = violationKindOf(violation.javaClass.name),
                        message = violation.message ?: violation.javaClass.simpleName,
                        stack = violation.stackTrace.map(StackTraceElement::toString),
                    )
                }
                .build(),
        )
    }

    private fun restoreStrictMode() {
        previousPolicy?.let(StrictMode::setThreadPolicy)
        previousPolicy = null
        appOwnsStrictMode = false
    }

    private fun startFrameTiming() {
        val app = currentApplicationOrNull() ?: return
        application = app
        frameThread = HandlerThread("jetwhale-frame-metrics").apply { start() }
        app.registerActivityLifecycleCallbacks(activityCallbacks)
        // The plugin is usually enabled after the first screen is up; it is picked up here rather
        // than on its next resume.
        mainHandler.post { resumedActivitiesOrEmpty().forEach(::attachFrameMetrics) }
    }

    private fun stopFrameTiming() {
        application?.unregisterActivityLifecycleCallbacks(activityCallbacks)
        application = null
        mainHandler.post {
            frameWindows.forEach { (window, listener) -> runCatching { window.removeOnFrameMetricsAvailableListener(listener) } }
            frameWindows.clear()
            frameThread?.quitSafely()
            frameThread = null
        }
    }

    private val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) = attachFrameMetrics(activity)

        override fun onActivityPaused(activity: Activity) = detachFrameMetrics(activity)

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    private fun attachFrameMetrics(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val window = activity.window ?: return
        val handler = frameThread?.let { Handler(it.looper) } ?: return
        if (window in frameWindows) return
        @Suppress("DEPRECATION")
        val refreshIntervalMillis = 1000.0 / activity.windowManager.defaultDisplay.refreshRate
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            recorder.frame(metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000.0, refreshIntervalMillis)
        }
        window.addOnFrameMetricsAvailableListener(listener, handler)
        frameWindows[window] = listener
    }

    private fun detachFrameMetrics(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val window = activity.window ?: return
        frameWindows.remove(window)?.let(window::removeOnFrameMetricsAvailableListener)
    }
}

/**
 * The thread policies an app has when it sets none: LAX, or the death-on-network policy
 * ActivityThread installs for every app since Android 3.0. Compared as strings, since a policy has
 * no equals and its mask is hidden.
 */
private val platformDefaultPolicies: Set<String> by lazy {
    setOf(
        StrictMode.ThreadPolicy.LAX.toString(),
        StrictMode.ThreadPolicy.Builder().detectNetwork().penaltyDeathOnNetwork().build().toString(),
    )
}

/**
 * The printer the app installed on the main Looper, if any, so it keeps receiving every line.
 * Looper has no getter for it; if the field cannot be read, there is assumed to be none.
 */
private fun currentLooperPrinter(): Printer? = try {
    Looper::class.java.getDeclaredField("mLogging").apply { isAccessible = true }.get(Looper.getMainLooper()) as? Printer
} catch (_: ReflectiveOperationException) {
    null
} catch (_: SecurityException) {
    null
}

/** The app's Application, found without the app passing one in, the way the agent runtime does. */
private fun currentApplicationOrNull(): Application? = try {
    Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as? Application
} catch (_: ReflectiveOperationException) {
    null
}

/**
 * The activities resumed right now, read from ActivityThread's records. There is no public API for
 * this; if the records cannot be read, frames are measured from the next activity that resumes.
 */
private fun resumedActivitiesOrEmpty(): List<Activity> = try {
    val activityThreadClass = Class.forName("android.app.ActivityThread")
    val activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null)
    val records = activityThreadClass.getDeclaredField("mActivities").apply { isAccessible = true }.get(activityThread) as Map<*, *>
    records.values.mapNotNull { record ->
        val recordClass = record?.javaClass ?: return@mapNotNull null
        val paused = recordClass.getDeclaredField("paused").apply { isAccessible = true }.getBoolean(record)
        val activity = recordClass.getDeclaredField("activity").apply { isAccessible = true }.get(record) as? Activity
        activity?.takeUnless { paused }
    }
} catch (_: ReflectiveOperationException) {
    emptyList()
} catch (_: ClassCastException) {
    emptyList()
}
