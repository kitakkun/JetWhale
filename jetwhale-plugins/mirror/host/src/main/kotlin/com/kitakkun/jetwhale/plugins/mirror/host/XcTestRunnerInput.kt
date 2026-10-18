package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunner
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerButton
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerException
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerStartException
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerTarget
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunners

/**
 * Taps, swipes, text and buttons sent to iOS simulators and devices through their XCTest runners,
 * in the pixels of the device's screen that the mirror works in.
 *
 * A command whose runner cannot start throws [XcTestRunnerStartException], so a caller with another
 * way to send input can use it; one the runner refuses throws [DeviceControlException].
 */
internal class XcTestRunnerInput(private val runners: XcTestRunners) {
    /** Why [target] takes no input, when that is known without starting a runner. */
    fun refusalFor(target: XcTestRunnerTarget): String? = runners.refusalFor(target)

    suspend fun tap(target: XcTestRunnerTarget, x: Int, y: Int) = withRunner(target) { it.tap(x / it.screen.scale, y / it.screen.scale) }

    suspend fun swipe(target: XcTestRunnerTarget, fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = withRunner(target) {
        val scale = it.screen.scale
        it.swipe(fromX = fromX / scale, fromY = fromY / scale, toX = toX / scale, toY = toY / scale, durationMillis = durationMillis)
    }

    suspend fun typeText(target: XcTestRunnerTarget, text: String) = withRunner(target) { it.typeText(text) }

    suspend fun pressButton(target: XcTestRunnerTarget, button: XcTestRunnerButton) = withRunner(target) { it.pressButton(button) }

    suspend fun openAppSwitcher(target: XcTestRunnerTarget) = withRunner(target, XcTestRunner::openAppSwitcher)

    /** The screen's size in pixels, as the runner reported it. */
    suspend fun screenSize(target: XcTestRunnerTarget): IntSize = withRunner(target) { IntSize(it.screen.widthPixels, it.screen.heightPixels) }

    /** Starts [target]'s runner ahead of its first input, without waiting for it. */
    fun startRunnerInBackground(target: XcTestRunnerTarget) = runners.startRunnerInBackground(target)

    private suspend fun <T> withRunner(target: XcTestRunnerTarget, command: suspend (XcTestRunner) -> T): T = try {
        command(runners.runnerFor(target))
    } catch (e: XcTestRunnerStartException) {
        throw e
    } catch (e: XcTestRunnerException) {
        throw DeviceControlException(e.message.orEmpty(), e)
    }
}
