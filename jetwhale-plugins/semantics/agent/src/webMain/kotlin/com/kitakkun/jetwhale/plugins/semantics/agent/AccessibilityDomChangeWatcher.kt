package com.kitakkun.jetwhale.plugins.semantics.agent

import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Tells a capture when Compose has caught up with the semantics in a viewport's accessibility DOM.
 *
 * Compose does not write the DOM as the semantics change. It waits until they have been still for
 * 100 ms, and while they keep changing it writes once a second. A capture taken in between reads
 * the screen as it was, which right after an action is the screen before it. Nothing in the page
 * announces a write still to come, so this watches the DOM and judges from when it last changed and
 * when the agent last acted:
 *
 * - a capture waits for the DOM to have been still for [QUIET_PERIOD], longer than Compose's 100 ms,
 *   so it does not land between two writes of one burst;
 * - after an action it also waits for the next write, the first that can carry the action's effect;
 * - and it never waits longer than [MAX_WAIT], by which time Compose has written everything it had
 *   seen when the capture began.
 *
 * The cost: a capture shortly after a change takes up to [QUIET_PERIOD], an action that changes no
 * semantics makes the next capture wait out [MAX_WAIT] for a write that never comes, and a screen
 * that keeps changing is read as Compose last wrote it, up to a second old.
 */
internal class AccessibilityDomChangeWatcher(accessibilityDom: AccessibilityDom) : AutoCloseable {
    private var lastChangeMark: TimeMark = TimeSource.Monotonic.markNow()
    private var actionAwaitingChangeMark: TimeMark? = null

    private val watch = accessibilityDom.watchChanges {
        lastChangeMark = TimeSource.Monotonic.markNow()
        actionAwaitingChangeMark = null
    }

    /** Makes the next capture wait for Compose to write the DOM again, as an action just changed the semantics. */
    fun expectChangeFromAction() {
        actionAwaitingChangeMark = TimeSource.Monotonic.markNow()
    }

    /** Suspends until the DOM reflects the semantics as they were when this was called, or [MAX_WAIT] has passed. */
    suspend fun awaitSettled() {
        val started = TimeSource.Monotonic.markNow()
        while (started.elapsedNow() < MAX_WAIT) {
            val actionStillAwaitingChange = actionAwaitingChangeMark?.let { it.elapsedNow() < MAX_WAIT } == true
            val quietFor = lastChangeMark.elapsedNow()
            if (!actionStillAwaitingChange && quietFor >= QUIET_PERIOD) return
            delay(if (actionStillAwaitingChange) POLL_INTERVAL else QUIET_PERIOD - quietFor)
        }
    }

    override fun close() {
        watch.close()
    }
}

/** Longer than the 100 ms Compose waits for the semantics to settle, with a few frames to spare for the recomposition before it. */
private val QUIET_PERIOD: Duration = 150.milliseconds

/** Compose writes the DOM at least once a second while the semantics keep changing; the rest is margin. */
private val MAX_WAIT: Duration = 1200.milliseconds

private val POLL_INTERVAL: Duration = 20.milliseconds
