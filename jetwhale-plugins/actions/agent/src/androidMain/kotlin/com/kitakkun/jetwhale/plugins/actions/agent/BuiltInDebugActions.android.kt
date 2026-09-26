package com.kitakkun.jetwhale.plugins.actions.agent

import android.app.Application
import android.app.LocaleManager
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.LocaleList
import android.os.Looper
import android.os.Process
import com.kitakkun.jetwhale.annotations.McpDescription
import kotlinx.serialization.Serializable

/** Long enough for the reply to the restart request to leave the process before it ends. */
private const val RESTART_DELAY_MILLIS = 500L

actual fun DebugActionsBuilder.platformBuiltInActions() {
    group("Built-in") {
        action("Restart app") {
            description = "Relaunches the app from its launcher activity in a new process. The debug session reconnects once the app is up again."
            run {
                val context = application()
                val launch = checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName)) { "the app has no launcher activity" }
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                context.startActivity(launch)
                Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, RESTART_DELAY_MILLIS)
                "Restarting"
            }
        }
        action<DeepLink>("Open deep link") {
            description = "Opens a URI inside this app, the way a tapped link or a notification would."
            run { args ->
                val context = application()
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(args.uri))
                    .setPackage(context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
        action<DarkMode>("Set dark mode") {
            description = "Overrides the app's dark mode, or returns it to following the system. Requires Android 12."
            run { args ->
                check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { "per-app dark mode needs Android 12 (API 31); this device runs API ${Build.VERSION.SDK_INT}" }
                val mode = when (args.mode) {
                    NightMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                    NightMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                    NightMode.DARK -> UiModeManager.MODE_NIGHT_YES
                }
                application().getSystemService(UiModeManager::class.java).setApplicationNightMode(mode)
            }
        }
        action<AppLanguage>("Set app language") {
            description = "Overrides the app's language, or returns it to the system's. Requires Android 13."
            run { args ->
                check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { "per-app languages need Android 13 (API 33); this device runs API ${Build.VERSION.SDK_INT}" }
                application().getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(args.languageTags)
            }
        }
    }
}

@Serializable
private data class DeepLink(
    @McpDescription("The URI to open, e.g. myapp://orders/42.")
    val uri: String,
)

@Serializable
private enum class NightMode { SYSTEM, LIGHT, DARK }

@Serializable
private data class DarkMode(
    @McpDescription("SYSTEM follows the device setting; LIGHT and DARK override it.")
    val mode: NightMode,
)

@Serializable
private data class AppLanguage(
    @McpDescription("BCP 47 language tags in order of preference, comma-separated, e.g. ja-JP,en. Empty returns the app to the system language.")
    val languageTags: String,
)

/**
 * The app's [Application], reached without the app passing a Context in: the hidden
 * `ActivityThread.currentApplication()` is what the agent runtime uses for the same purpose.
 */
private fun application(): Context = checkNotNull(
    Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as? Application,
) { "the application is not available" }
