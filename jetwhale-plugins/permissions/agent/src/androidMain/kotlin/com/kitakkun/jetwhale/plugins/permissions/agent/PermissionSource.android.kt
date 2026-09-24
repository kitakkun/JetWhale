package com.kitakkun.jetwhale.plugins.permissions.agent

import android.app.Activity
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.Application
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionActionResult
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionCategory
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionState
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionStatus
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import java.util.Collections

/** The id of the app-wide notification switch, which is not a manifest permission. */
private const val NOTIFICATIONS_ID = "android.notifications"

/** Tags the permission dialogs this plugin starts, so an app's own result handling can ignore them. */
private const val REQUEST_CODE = 0x4A57

internal actual fun platformPermissionSource(): PermissionSource {
    val application = currentApplicationOrNull() ?: return UnsupportedPermissionSource(
        platform = "Android",
        reason = "The app's Application could not be found; register the plugin from Application.onCreate or later.",
    )
    return AndroidPermissionSource(application)
}

private class AndroidPermissionSource(private val application: Application) : PermissionSource {
    override val platform: String get() = "Android"
    override val unsupportedReason: String? get() = null

    private val foreground = ForegroundActivityTracker(application)

    // Only a request this plugin made tells a permanent denial apart from "never asked".
    private val requestedByPlugin: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    override suspend fun read(): List<PermissionState> {
        val activity = foreground.current
        val declared = declaredPermissions().map { stateOf(it, activity) }
        return declared + listOfNotNull(notificationSwitchState())
    }

    override suspend fun request(id: String): PermissionActionResult {
        if (id == NOTIFICATIONS_ID) return openNotificationSettings()
        if (id !in declaredPermissions()) {
            return failure("the app does not declare $id in its manifest; Android grants only declared permissions")
        }
        SPECIAL_ACCESSES[id]?.let { special -> return openSpecialAccess(id, special) }
        if (classOf(id)?.category != PermissionCategory.Runtime) {
            return failure("$id is decided at install; it cannot be requested while the app runs")
        }
        val activity = foreground.current ?: return failure("no activity of the app is in the foreground to show the permission dialog")
        onMainThread { activity.requestPermissions(arrayOf(id), REQUEST_CODE) }
        requestedByPlugin += id
        return PermissionActionResult(message = "Asked for ${labelOf(id)}; the user's choice arrives as a change.", error = null)
    }

    override suspend fun openAppSettings(): PermissionActionResult = startSettings(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri()),
        opened = "Opened the app's settings page.",
    )

    private fun stateOf(permission: String, activity: Activity?): PermissionState {
        SPECIAL_ACCESSES[permission]?.let { special ->
            return PermissionState(
                id = permission,
                label = labelOf(permission),
                category = PermissionCategory.SpecialAccess,
                protection = classOf(permission)?.label,
                status = if (special.isGranted(application)) PermissionStatus.Granted else PermissionStatus.Denied,
                requestable = special.settingsIntent(application) != null,
                note = "Granted by the user on a settings screen of its own.",
            )
        }
        val protection = classOf(permission)
        val granted = application.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        val runtime = protection?.category == PermissionCategory.Runtime
        return PermissionState(
            id = permission,
            label = labelOf(permission),
            category = protection?.category ?: PermissionCategory.InstallTime,
            protection = protection?.label ?: "unknown to this Android version",
            status = if (granted) PermissionStatus.Granted else PermissionStatus.Denied,
            requestable = runtime && !granted && activity != null,
            note = when {
                granted -> null
                !runtime -> "Not granted at install; it cannot be requested while the app runs."
                activity == null -> "No activity is in the foreground, so whether a request would show a dialog is unknown."
                activity.shouldShowRequestPermissionRationale(permission) -> "Denied once; a request shows the dialog again."
                permission in requestedByPlugin -> "Denied permanently: a request no longer shows a dialog. Change it in the app's settings."
                else -> "Never asked, or denied permanently; Android does not tell these apart until the app asks."
            },
        )
    }

    private fun notificationSwitchState(): PermissionState? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        val enabled = application.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        return PermissionState(
            id = NOTIFICATIONS_ID,
            label = "Notifications (app switch)",
            category = PermissionCategory.SpecialAccess,
            protection = null,
            status = if (enabled) PermissionStatus.Granted else PermissionStatus.Denied,
            requestable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O,
            note = "The app-wide switch in the notification settings. From Android 13 the POST_NOTIFICATIONS runtime permission also has to be granted.",
        )
    }

    @Suppress("DEPRECATION") // The int-flag overload is the one that exists on every supported API level.
    private fun declaredPermissions(): List<String> =
        application.packageManager.getPackageInfo(application.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions?.toList().orEmpty()

    @Suppress("DEPRECATION") // protectionLevel still carries base level and flags together, which is what classifyProtection reads.
    private fun classOf(permission: String): ProtectionClass? = try {
        classifyProtection(application.packageManager.getPermissionInfo(permission, 0).protectionLevel)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private suspend fun openNotificationSettings(): PermissionActionResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return failure("the notification settings screen needs Android 8.0 or later")
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, application.packageName)
        return startSettings(intent, opened = "Opened the app's notification settings.")
    }

    private suspend fun openSpecialAccess(id: String, special: SpecialAccess): PermissionActionResult {
        val intent = special.settingsIntent(application) ?: return failure("this Android version has no settings screen for ${labelOf(id)}")
        return startSettings(intent, opened = "Opened the settings screen for ${labelOf(id)}.")
    }

    private suspend fun startSettings(intent: Intent, opened: String): PermissionActionResult = try {
        onMainThread { application.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        PermissionActionResult(message = opened, error = null)
    } catch (e: ActivityNotFoundException) {
        failure("no settings screen handles this on the device: ${e.message}")
    }

    private fun packageUri(): Uri = Uri.fromParts("package", application.packageName, null)
}

private fun failure(error: String) = PermissionActionResult(message = "", error = error)

private fun labelOf(permission: String): String = permission.substringAfterLast('.')

/**
 * A permission the user grants on a settings screen rather than in a dialog. Each has its own
 * query, since checkSelfPermission does not reflect these switches.
 */
private class SpecialAccess(
    val isGranted: (Context) -> Boolean,
    val settingsIntent: (Context) -> Intent?,
)

private val SPECIAL_ACCESSES: Map<String, SpecialAccess> = mapOf(
    "android.permission.SYSTEM_ALERT_WINDOW" to SpecialAccess(
        isGranted = Settings::canDrawOverlays,
        settingsIntent = { Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUriOf(it)) },
    ),
    "android.permission.WRITE_SETTINGS" to SpecialAccess(
        isGranted = Settings.System::canWrite,
        settingsIntent = { Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, packageUriOf(it)) },
    ),
    "android.permission.SCHEDULE_EXACT_ALARM" to SpecialAccess(
        isGranted = { Build.VERSION.SDK_INT < Build.VERSION_CODES.S || it.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() },
        settingsIntent = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUriOf(it)) else null },
    ),
    "android.permission.MANAGE_EXTERNAL_STORAGE" to SpecialAccess(
        isGranted = { Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager() },
        settingsIntent = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUriOf(it)) else null },
    ),
    "android.permission.PACKAGE_USAGE_STATS" to SpecialAccess(
        isGranted = ::hasUsageStatsAccess,
        settingsIntent = { Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS) },
    ),
    "android.permission.REQUEST_INSTALL_PACKAGES" to SpecialAccess(
        isGranted = { Build.VERSION.SDK_INT < Build.VERSION_CODES.O || it.packageManager.canRequestPackageInstalls() },
        settingsIntent = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUriOf(it)) else null },
    ),
    // A normal permission by its protection level, but what it unlocks is the battery-optimization
    // exemption the user toggles in the settings.
    "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" to SpecialAccess(
        isGranted = { it.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(it.packageName) },
        settingsIntent = { Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUriOf(it)) },
    ),
)

private fun packageUriOf(context: Context): Uri = Uri.fromParts("package", context.packageName, null)

@Suppress("DEPRECATION") // checkOpNoThrow is the only form below API 29.
private fun hasUsageStatsAccess(context: Context): Boolean {
    val appOps = context.getSystemService(AppOpsManager::class.java)
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    } else {
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

/**
 * The app's activity that is currently resumed. A permission dialog needs one, and so does telling
 * a first denial from a permanent one; the tracker starts with the plugin, so the activity already
 * on screen at that moment is picked up at its next resume.
 */
private class ForegroundActivityTracker(application: Application) {
    @Volatile
    private var resumed: WeakReference<Activity>? = null

    val current: Activity? get() = resumed?.get()

    init {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    resumed = WeakReference(activity)
                }

                override fun onActivityPaused(activity: Activity) {
                    if (resumed?.get() === activity) resumed = null
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }
}

/** Runs [block] on the main thread and hands its outcome, a failure included, back to the caller. */
private suspend fun onMainThread(block: () -> Unit) = suspendCancellableCoroutine { continuation ->
    Handler(Looper.getMainLooper()).post { continuation.resumeWith(runCatching(block)) }
}

/** The app's Application, reached the same way the agent runtime does: no Context has to be passed in. */
private fun currentApplicationOrNull(): Application? = try {
    Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as? Application
} catch (_: Exception) {
    // A hidden-API restriction surfaces as a SecurityException rather than a reflective failure;
    // either way the plugin reports itself unsupported instead of failing the app's startup.
    null
}
