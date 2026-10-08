package com.kitakkun.jetwhale.plugins.semantics.agent

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.objcPtr
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowDidBecomeHiddenNotification
import platform.UIKit.UIWindowDidBecomeVisibleNotification
import platform.UIKit.UIWindowScene
import platform.darwin.NSObjectProtocol

/**
 * Registers every window of the app with [ComposeNodeSourceRegistry], so the Compose Semantics
 * Inspector can read UIKit, SwiftUI and Compose Multiplatform content alike.
 *
 * Call it once at startup, on the main thread — from your `App`'s `init`, or from
 * `application(_:didFinishLaunchingWithOptions:)` — before or after `startJetWhale`. It scans the
 * windows that already exist and then follows `UIWindowDidBecomeVisible` /
 * `UIWindowDidBecomeHidden` for the rest, so a window that opens later is registered as it appears.
 * An app without scenes — one still driven by `UIApplicationDelegate` alone — is covered through
 * `UIApplication.windows`. The keyboard's windows are left out: they belong to the system, not the
 * app.
 *
 * The tree is there to read only while iOS has **application accessibility** on: without it UIKit
 * reports no accessibility frames and SwiftUI lists none of its content, Compose included. On a
 * simulator the install turns it on, unless [enableSimulatorApplicationAccessibility] is `false`.
 * The setting belongs to the simulator, not the app, so it stays on for every app on that simulator
 * until something turns it off. On a device nothing is changed: there the setting is on while an
 * assistive feature such as VoiceOver, or the Accessibility Inspector, has turned it on.
 *
 * The install is process-wide and idempotent: calling it twice returns the same handle. Closing it
 * unregisters every window and stops following notifications; it leaves application accessibility as
 * it is.
 */
fun installJetWhaleSemanticsProbe(enableSimulatorApplicationAccessibility: Boolean = true): AutoCloseable = IosSemanticsProbe.install(enableSimulatorApplicationAccessibility)

/**
 * Turns on the simulator's application accessibility, which UIKit checks before it loads its
 * accessibility support into an app. It takes effect in the running app without a relaunch.
 */
internal expect fun enableApplicationAccessibilityOnSimulator()

private object IosSemanticsProbe {
    private var installation: Installation? = null

    fun install(enableSimulatorApplicationAccessibility: Boolean): AutoCloseable = installation ?: run {
        if (enableSimulatorApplicationAccessibility) enableApplicationAccessibilityOnSimulator()
        Installation().also { installation = it }
    }

    fun uninstalled(installation: Installation) {
        if (this.installation === installation) this.installation = null
    }

    @OptIn(ExperimentalForeignApi::class)
    class Installation : AutoCloseable {
        private class Tracked(val window: UIWindow, val registration: ComposeNodeSourceRegistry.Registration)

        // Keyed by address rather than by the window object: a notification may hand over a
        // different Kotlin wrapper for the same window, and the address is what identifies it.
        private val tracked = HashMap<Long, Tracked>()
        private val observers: List<NSObjectProtocol>

        init {
            val center = NSNotificationCenter.defaultCenter
            observers = listOf(
                center.addObserverForName(UIWindowDidBecomeVisibleNotification, `object` = null, queue = NSOperationQueue.mainQueue) { notification ->
                    notification?.window()?.let(::track)
                },
                center.addObserverForName(UIWindowDidBecomeHiddenNotification, `object` = null, queue = NSOperationQueue.mainQueue) { notification ->
                    notification?.window()?.let(::untrack)
                },
            )
            val application = UIApplication.sharedApplication
            val sceneWindows = application.connectedScenes
                .mapNotNull { it as? UIWindowScene }
                .flatMap { scene -> scene.windows.map { it as UIWindow } }

            // Deprecated since iOS 15, but an app that has not adopted scenes keeps its windows
            // only here.
            @Suppress("DEPRECATION")
            val legacyWindows = application.windows.map { it as UIWindow }
            (sceneWindows + legacyWindows)
                .filter { !it.hidden }
                .forEach(::track)
        }

        private fun track(window: UIWindow) {
            val address = window.objcPtr().toLong()
            if (window.isSystemWindow() || address in tracked) return
            tracked[address] = Tracked(window, ComposeNodeSourceRegistry.register(IosWindowNodeSource(window)))
        }

        private fun untrack(window: UIWindow) {
            tracked.remove(window.objcPtr().toLong())?.registration?.close()
            AppleNodeIds.release(window)
        }

        override fun close() {
            observers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
            tracked.values.toList().forEach { untrack(it.window) }
            IosSemanticsProbe.uninstalled(this)
        }

        private fun NSNotification.window(): UIWindow? = `object` as? UIWindow

        /** The keyboard and the text-effects overlay are windows UIKit opens for itself, with nothing of the app's in them. */
        private fun UIWindow.isSystemWindow(): Boolean {
            val name = className()
            return name.contains("Keyboard") || name.contains("TextEffects")
        }
    }
}
