package com.kitakkun.jetwhale.plugins.semantics.agent

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
 * The install is process-wide and idempotent: calling it twice returns the same handle. Closing it
 * unregisters every window and stops following notifications.
 */
fun installJetWhaleSemanticsProbe(): AutoCloseable = IosSemanticsProbe.install()

private object IosSemanticsProbe {
    private var installation: Installation? = null

    fun install(): AutoCloseable = installation ?: Installation().also { installation = it }

    fun uninstalled(installation: Installation) {
        if (this.installation === installation) this.installation = null
    }

    class Installation : AutoCloseable {
        private val registrations = HashMap<UIWindow, ComposeNodeSourceRegistry.Registration>()
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

            @Suppress("DEPRECATION")
            val legacyWindows = application.windows.map { it as UIWindow }
            (sceneWindows + legacyWindows)
                .filter { !it.hidden }
                .forEach(::track)
        }

        private fun track(window: UIWindow) {
            if (window.isSystemWindow() || window in registrations) return
            registrations[window] = ComposeNodeSourceRegistry.register(IosWindowNodeSource(window))
        }

        private fun untrack(window: UIWindow) {
            registrations.remove(window)?.close()
            // No capture will run for this window again, so what the last one retained is released here.
            AppleNodeIds.release(window)
        }

        override fun close() {
            observers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
            registrations.keys.toList().forEach(::untrack)
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
