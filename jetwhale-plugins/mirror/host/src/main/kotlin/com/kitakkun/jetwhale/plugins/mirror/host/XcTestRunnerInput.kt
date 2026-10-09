package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunner
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerButton
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerException
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerPointSpace
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerTarget
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunners
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.time.Instant
import kotlin.time.Duration

/**
 * Taps, swipes, text and buttons sent to iOS simulators and devices through their XCTest runners,
 * and their screens streamed from them, in the pixels of the device's screen that the mirror works
 * in. A command the runner refuses, or whose runner cannot start, throws [DeviceControlException]
 * with the reason.
 */
internal class XcTestRunnerInput(private val runners: XcTestRunners) {
    /** Why [target] takes no input, when that is known without starting a runner. */
    fun refusalFor(target: XcTestRunnerTarget): String? = runners.refusalFor(target)

    /** Taps at [x], [y] in pixels of [space]. */
    suspend fun tap(target: XcTestRunnerTarget, x: Int, y: Int, space: XcTestRunnerPointSpace) = withRunner(target) { it.tap(x / it.screen.scale, y / it.screen.scale, space) }

    suspend fun swipe(target: XcTestRunnerTarget, fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int, space: XcTestRunnerPointSpace) = withRunner(target) {
        val scale = it.screen.scale
        it.swipe(fromX = fromX / scale, fromY = fromY / scale, toX = toX / scale, toY = toY / scale, durationMillis = durationMillis, space = space)
    }

    /** Keeps [target]'s runner from stopping when idle for [duration]; returns the time that ends. */
    suspend fun keepRunnerAlive(target: XcTestRunnerTarget, duration: Duration): Instant = withRunner(target) { it.keepAlive(duration) }

    /** Until when a lease keeps [target]'s runner from stopping when idle, or null. */
    fun runnerKeptAliveUntil(target: XcTestRunnerTarget): Instant? = runners.keptAliveUntil(target)

    suspend fun typeText(target: XcTestRunnerTarget, text: String) = withRunner(target) { it.typeText(text) }

    suspend fun pressButton(target: XcTestRunnerTarget, button: XcTestRunnerButton) = withRunner(target) { it.pressButton(button) }

    suspend fun openAppSwitcher(target: XcTestRunnerTarget) = withRunner(target, XcTestRunner::openAppSwitcher)

    /** The size of [target]'s screen in device-native pixels, as its runner reports it. */
    suspend fun screenSize(target: XcTestRunnerTarget): IntSize = withRunner(target) { IntSize(it.screen.widthPixels, it.screen.heightPixels) }

    /**
     * [target]'s screen as JPEG frames, at most [maxFps] a second, starting its runner first when none
     * runs; see [XcTestRunner.streamScreenAsJpeg]. The flow fails with [DeviceControlException].
     */
    fun streamScreenAsJpeg(target: XcTestRunnerTarget, maxFps: Int): Flow<ByteArray> = flow { emitAll(runners.runnerFor(target).streamScreenAsJpeg(maxFps)) }
        .catch { e -> throw if (e is XcTestRunnerException) DeviceControlException(e.message.orEmpty(), e) else e }

    /** Starts [target]'s runner ahead of its first input, without waiting for it. */
    fun startRunnerInBackground(target: XcTestRunnerTarget) = runners.startRunnerInBackground(target)

    private suspend fun <T> withRunner(target: XcTestRunnerTarget, command: suspend (XcTestRunner) -> T): T = try {
        command(runners.runnerFor(target))
    } catch (e: XcTestRunnerException) {
        throw DeviceControlException(e.message.orEmpty(), e)
    }
}
