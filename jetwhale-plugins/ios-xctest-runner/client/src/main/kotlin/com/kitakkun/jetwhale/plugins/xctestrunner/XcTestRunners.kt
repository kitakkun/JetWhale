package com.kitakkun.jetwhale.plugins.xctestrunner

import kotlinx.coroutines.flow.Flow
import java.io.File
import java.time.Instant
import kotlin.time.Duration

/** An iOS simulator or physical device that an XCTest runner drives, by its UDID. */
public sealed interface XcTestRunnerTarget {
    public val udid: String

    /**
     * The major version of the iOS it runs, such as 17, which decides whether a runner can run on it;
     * null when unknown, in which case a runner is tried.
     */
    public val iosMajorVersion: Int?

    public class Simulator(override val udid: String, override val iosMajorVersion: Int?) : XcTestRunnerTarget

    /** A physical device, which needs a runner signed for the development team in [XcTestRunnerSettings]. */
    public class Device(override val udid: String, override val iosMajorVersion: Int?) : XcTestRunnerTarget
}

/** What starting a runner needs from the user. */
public interface XcTestRunnerSettings {
    /** The Apple development team that signs the runner for physical devices, such as `ABCDE12345`; null until set. */
    public val developmentTeam: String?
}

/**
 * The XCTest runners on this machine: one per simulator or device, shared by every plugin that
 * drives iOS. A plugin asks for the runner of a target and gets the one already running, whoever
 * started it, or a new one. A runner ends itself after a while without commands.
 */
public interface XcTestRunners {
    /**
     * Why [target] cannot be driven, when that is known without trying: an iOS older than the
     * runner runs on, or no development team or no `iproxy` for a device. Null when a runner can be tried, which may still fail to
     * start; [runnerFor] then says why.
     */
    public fun refusalFor(target: XcTestRunnerTarget): String?

    /**
     * The runner driving [target]: the running one, or a new one, built first when this Xcode has
     * not built it yet, which takes seconds for a simulator and longer for a device.
     *
     * @throws XcTestRunnerStartException when no runner can be started.
     */
    public suspend fun runnerFor(target: XcTestRunnerTarget): XcTestRunner

    /**
     * Starts [target]'s runner without waiting for it, so that its first command does not wait for
     * a build and a start. A target whose last start failed is left alone until [runnerFor] tries it
     * again.
     */
    public fun startRunnerInBackground(target: XcTestRunnerTarget)

    /**
     * Until when a lease keeps [target]'s runner from stopping when idle, as recorded for every
     * client; null when no lease runs. See [XcTestRunner.keepAlive].
     */
    public fun keptAliveUntil(target: XcTestRunnerTarget): Instant?

    public companion object {
        /**
         * The runners this Mac starts with Xcode, through [xcrunPath], keeping their builds and
         * their shared state under [stateDirectory], which every plugin must agree on. A device's
         * runner is reached through [iproxyPath] and signed with [settings]' team.
         */
        public fun onThisMac(stateDirectory: File, xcrunPath: String, iproxyPath: String?, settings: XcTestRunnerSettings): XcTestRunners = LocalXcTestRunners.onThisMac(stateDirectory, xcrunPath, iproxyPath, settings)
    }
}

/**
 * One device's runner. A point is in the [XcTestRunnerPointSpace] its command names; divide pixels
 * by [XcTestRunnerScreen.scale] to get points. A runner that went away between commands is
 * replaced, and the command sent to the new one.
 *
 * Every command throws [XcTestRunnerException] when the runner refuses it, and
 * [XcTestRunnerStartException] when no runner can be started in place of one that went away.
 */
public interface XcTestRunner {
    public val screen: XcTestRunnerScreen

    public suspend fun tap(x: Double, y: Double, space: XcTestRunnerPointSpace)

    public suspend fun longPress(x: Double, y: Double, durationMillis: Int, space: XcTestRunnerPointSpace)

    public suspend fun swipe(fromX: Double, fromY: Double, toX: Double, toY: Double, durationMillis: Int, space: XcTestRunnerPointSpace)

    /** The screen as the interface shows it now, which a device turned to landscape shows sideways. */
    public suspend fun interfaceScreen(): XcTestRunnerInterfaceScreen

    /**
     * Types [text] into whatever has keyboard focus on the device. A long text is typed in parts, one
     * after another, so one that fails part way leaves the parts before it typed.
     */
    public suspend fun typeText(text: String)

    public suspend fun pressButton(button: XcTestRunnerButton)

    /** Opens the app switcher with the swipe up from the bottom edge that a Face ID iPhone takes. */
    public suspend fun openAppSwitcher()

    /** Brings the app with [bundleId] to the foreground, launching it if it is not running. */
    public suspend fun activateApp(bundleId: String)

    /**
     * The device's screen as JPEG frames, at most [maxFps] a second, from 1 to 60, for as long as the
     * flow is collected; a collector slower than that gets the newest frame next, and the ones in
     * between are dropped rather than queued. A frame shows the screen as the interface does, as a
     * screenshot would: turned to landscape, its pixels stay portrait and its EXIF orientation turns
     * them, which decoders that read EXIF, Skia among them, apply. While a stream is open the runner
     * does not stop when idle.
     *
     * A runner that goes away while streaming is replaced, as for a command, and the stream goes on
     * from the new one. The flow fails with [XcTestRunnerStartException] when no runner can be
     * started in its place, and with [XcTestRunnerException] when the stream breaks again before
     * its first frame.
     */
    public fun streamScreenAsJpeg(maxFps: Int): Flow<ByteArray>

    /**
     * Keeps the runner from stopping when idle for [duration] from now, at most two hours, and
     * records the lease where every client sees it; [Duration.ZERO] ends a lease, and a negative
     * [duration] is refused. A lease nobody renews ends, so a forgotten one cannot keep a runner
     * going. Returns at once, with the time the lease ends.
     */
    public suspend fun keepAlive(duration: Duration): Instant
}

/** The space a point is given in. */
public enum class XcTestRunnerPointSpace(internal val wireName: String) {
    /** Device-native portrait points, whatever the interface orientation: the space XCTest synthesizes touches in. */
    Device("device"),

    /** Points in the interface orientation: the space of screenshots, of [XcTestRunner.streamScreenAsJpeg]'s frames and of XCTest's element frames. */
    Screen("screen"),
}

/** How the interface is turned, by UIKit's names: `LandscapeRight` has the home side on the right. */
public enum class XcTestRunnerOrientation(internal val wireName: String) {
    Portrait("portrait"),
    PortraitUpsideDown("portraitUpsideDown"),
    LandscapeLeft("landscapeLeft"),
    LandscapeRight("landscapeRight"),
}

/** The screen as the interface shows it: how it is turned, and its size in pixels that way round. */
public class XcTestRunnerInterfaceScreen(
    public val orientation: XcTestRunnerOrientation,
    public val widthPixels: Int,
    public val heightPixels: Int,
)

/** The device's screen in portrait: its size in pixels, and how many pixels make a point. */
public class XcTestRunnerScreen(public val widthPixels: Int, public val heightPixels: Int, public val scale: Double)

/** The hardware buttons a runner presses. A simulator has no volume buttons to press. */
public enum class XcTestRunnerButton(internal val wireName: String) {
    Home("home"),
    Lock("lock"),
    VolumeUp("volumeUp"),
    VolumeDown("volumeDown"),
}

/** A runner refused a command, or could not be reached; [message] says why, for the user. */
public open class XcTestRunnerException(message: String, cause: Throwable?) : Exception(message, cause)

/** No runner could be built or started, so nothing was sent; [message] says what to do about it. */
public class XcTestRunnerStartException(message: String, cause: Throwable?) : XcTestRunnerException(message, cause)
