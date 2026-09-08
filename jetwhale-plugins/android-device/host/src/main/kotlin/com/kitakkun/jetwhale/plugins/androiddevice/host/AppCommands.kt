package com.kitakkun.jetwhale.plugins.androiddevice.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdb
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import kotlinx.serialization.json.put
import java.io.File

internal const val PACKAGE_NAME_DESCRIPTION = "Application id of the app, e.g. com.example.qa.sample."

private val PACKAGE_NAME_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*")

/**
 * Everything reaching the device's shell is single-quoted, so this is not about escaping — it is
 * about failing on a value that cannot be a package name at all, rather than passing it to `pm` and
 * reporting whatever it says.
 */
@OptIn(ExperimentalJetWhaleApi::class)
internal fun requirePackageName(value: String): String {
    if (!PACKAGE_NAME_PATTERN.matches(value)) throw JetWhaleMcpArgumentException("invalid packageName: $value (expected an application id such as com.example.qa.sample)")
    return value
}

@OptIn(ExperimentalJetWhaleApi::class)
internal class InstallApkCommand(adb: JetWhaleAdb) : AndroidDeviceCommand(adb) {
    override val name = "$TOOL_PREFIX.installApk"
    override val description =
        "Installs an APK from this machine onto the device. Destructive: with reinstall it replaces " +
            "the installed build of the same application id. Reports the install failure reason " +
            "(INSTALL_FAILED_*) rather than only an exit code."

    private val apkPath by string("Absolute path to the APK on the machine running the debug tool.")
    private val reinstall by booleanOrNull("Pass -r to replace an already installed build. Defaults to true, because a QA loop installs the same app over and over.")
    private val grantPermissions by booleanOrNull("Pass -g to grant every runtime permission the manifest declares. Defaults to true, so a QA run is not stopped by a permission dialog.")

    override suspend fun executeOnDevice(arguments: JetWhaleMcpArguments, target: DeviceTarget): JetWhaleMcpResult {
        val apk = File(arguments[apkPath])
        if (!apk.isAbsolute) throw JetWhaleMcpArgumentException("invalid apkPath: ${apk.path} (expected an absolute path)")
        if (!apk.isFile) throw JetWhaleMcpArgumentException("invalid apkPath: ${apk.path} (no such file)")

        val flags = buildList {
            if (arguments[reinstall] ?: true) add("-r")
            if (arguments[grantPermissions] ?: true) add("-g")
        }
        val result = target.adb("install", *flags.toTypedArray(), apk.absolutePath, timeout = AdbTimeouts.PACKAGE)
        val failure = parseInstallFailure(result.output)
        if (result.exitCode != 0 || failure != null) {
            return target.failureResult(result, failure ?: "install failed")
        }
        return target.successResult {
            put("apkPath", apk.absolutePath)
            put("output", result.output.trim())
        }
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
internal class UninstallAppCommand(adb: JetWhaleAdb) : AndroidDeviceCommand(adb) {
    override val name = "$TOOL_PREFIX.uninstallApp"
    override val description = "Uninstalls an app. Destructive: it removes the app and, unless keepData is set, everything it stored."

    private val packageName by string(PACKAGE_NAME_DESCRIPTION)
    private val keepData by booleanOrNull("Pass -k to keep the app's data and cache directories. Defaults to false, which is what \"uninstall\" normally means.")

    override suspend fun executeOnDevice(arguments: JetWhaleMcpArguments, target: DeviceTarget): JetWhaleMcpResult {
        val packageName = requirePackageName(arguments[packageName])
        val flags = if (arguments[keepData] ?: false) arrayOf("-k") else emptyArray()
        val result = target.adb("uninstall", *flags, packageName, timeout = AdbTimeouts.PACKAGE)
        if (result.exitCode != 0 || !result.output.contains("Success")) {
            return target.failureResult(result, "uninstall failed")
        }
        return target.successResult { put("packageName", packageName) }
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
internal class AppInfoCommand(adb: JetWhaleAdb) : AndroidDeviceCommand(adb) {
    override val name = "$TOOL_PREFIX.appInfo"
    override val description =
        "Reports whether an app is installed and, when it is, its version: {installed, versionName, " +
            "versionCode, firstInstallTime}. Use it to confirm an install took, or which build is on the device."

    private val packageName by string(PACKAGE_NAME_DESCRIPTION)

    override suspend fun executeOnDevice(arguments: JetWhaleMcpArguments, target: DeviceTarget): JetWhaleMcpResult {
        val packageName = requirePackageName(arguments[packageName])
        val result = target.shell("dumpsys", "package", singleQuoteForShell(packageName), timeout = AdbTimeouts.SHELL)
        target.requireSuccess(result, "dumpsys package was refused")?.let { return it }

        val app = parseInstalledApp(result.output, packageName)
        return target.successResult {
            put("packageName", packageName)
            put("installed", app != null)
            put("versionName", app?.versionName)
            put("versionCode", app?.versionCode)
            put("firstInstallTime", app?.firstInstallTime)
        }
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
internal class LaunchAppCommand(adb: JetWhaleAdb) : AndroidDeviceCommand(adb) {
    override val name = "$TOOL_PREFIX.launchApp"
    override val description =
        "Launches an app. With no activity given, the launcher activity is resolved first and " +
            "started by name. The app is not force-stopped beforehand, so an already running app is " +
            "brought forward as it would be by a real launch — call stopApp first when a cold start is wanted."

    private val packageName by string(PACKAGE_NAME_DESCRIPTION)
    private val activity by stringOrNull("Activity to start, e.g. .MainActivity or com.example.qa.sample.MainActivity. Omit to use the launcher activity.")

    override suspend fun executeOnDevice(arguments: JetWhaleMcpArguments, target: DeviceTarget): JetWhaleMcpResult {
        val packageName = requirePackageName(arguments[packageName])
        val component = arguments[activity]?.let { "$packageName/$it" } ?: run {
            val resolved = target.shell(
                "cmd",
                "package",
                "resolve-activity",
                "--brief",
                "-c",
                "android.intent.category.LAUNCHER",
                singleQuoteForShell(packageName),
                timeout = AdbTimeouts.SHELL,
            )
            target.requireSuccess(resolved, "resolve-activity was refused")?.let { return it }
            val activity = parseResolvedActivity(resolved.output)
                ?: return target.failureResult(resolved, "$packageName has no launcher activity (is it installed?)")
            "${activity.packageName}/${activity.activity}"
        }

        val started = target.shell("am", "start", "-n", singleQuoteForShell(component), timeout = AdbTimeouts.SHELL)
        if (started.exitCode != 0 || started.output.contains("Error:")) {
            return target.failureResult(started, "am start failed")
        }
        return target.successResult {
            put("packageName", packageName)
            put("component", component)
            put("output", started.output.trim())
        }
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
internal class StopAppCommand(adb: JetWhaleAdb) : AndroidDeviceCommand(adb) {
    override val name = "$TOOL_PREFIX.stopApp"
    override val description = "Force-stops an app. Destructive to its in-memory state: the next launch is a cold start."

    private val packageName by string(PACKAGE_NAME_DESCRIPTION)

    override suspend fun executeOnDevice(arguments: JetWhaleMcpArguments, target: DeviceTarget): JetWhaleMcpResult {
        val packageName = requirePackageName(arguments[packageName])
        val result = target.shell("am", "force-stop", singleQuoteForShell(packageName), timeout = AdbTimeouts.SHELL)
        target.requireSuccess(result, "am force-stop was refused")?.let { return it }
        return target.successResult { put("packageName", packageName) }
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
internal class ClearAppDataCommand(adb: JetWhaleAdb) : AndroidDeviceCommand(adb) {
    override val name = "$TOOL_PREFIX.clearAppData"
    override val description =
        "Clears an app's data, taking it back to a first-run state. Destructive: databases, " +
            "preferences, caches and granted runtime permissions are all gone. The app is also stopped."

    private val packageName by string(PACKAGE_NAME_DESCRIPTION)

    override suspend fun executeOnDevice(arguments: JetWhaleMcpArguments, target: DeviceTarget): JetWhaleMcpResult {
        val packageName = requirePackageName(arguments[packageName])
        val result = target.shell("pm", "clear", singleQuoteForShell(packageName), timeout = AdbTimeouts.PACKAGE)
        if (result.exitCode != 0 || !result.output.contains("Success")) {
            return target.failureResult(result, "pm clear failed")
        }
        return target.successResult { put("packageName", packageName) }
    }
}

/** `pm grant` and `pm revoke` differ only in the verb, so one class covers both. */
@OptIn(ExperimentalJetWhaleApi::class)
internal class PermissionCommand(adb: JetWhaleAdb, private val grant: Boolean) : AndroidDeviceCommand(adb) {
    override val name = if (grant) "$TOOL_PREFIX.grantPermission" else "$TOOL_PREFIX.revokePermission"
    override val description = if (grant) {
        "Grants a runtime permission to an app, so a QA run reaches the screen behind the permission dialog."
    } else {
        "Revokes a runtime permission from an app, to exercise the denied path. Destructive: revoking a permission can restart the app's process."
    }

    private val packageName by string(PACKAGE_NAME_DESCRIPTION)
    private val permission by string("Full permission name, e.g. android.permission.CAMERA.")

    override suspend fun executeOnDevice(arguments: JetWhaleMcpArguments, target: DeviceTarget): JetWhaleMcpResult {
        val packageName = requirePackageName(arguments[packageName])
        val permission = arguments[permission]
        val verb = if (grant) "grant" else "revoke"
        val result = target.shell("pm", verb, singleQuoteForShell(packageName), singleQuoteForShell(permission), timeout = AdbTimeouts.SHELL)
        target.requireSuccess(result, "pm $verb failed")?.let { return it }
        return target.successResult {
            put("packageName", packageName)
            put("permission", permission)
            put("granted", grant)
        }
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
internal class CurrentActivityCommand(adb: JetWhaleAdb) : AndroidDeviceCommand(adb) {
    override val name = "$TOOL_PREFIX.currentActivity"
    override val description =
        "Reports the activity in the foreground as {packageName, activity}. Use it to confirm that a " +
            "launch or a navigation landed where it was supposed to."

    override suspend fun executeOnDevice(arguments: JetWhaleMcpArguments, target: DeviceTarget): JetWhaleMcpResult {
        val result = target.shell("dumpsys", "activity", "activities", timeout = AdbTimeouts.SHELL)
        target.requireSuccess(result, "dumpsys activity activities was refused")?.let { return it }

        val top = parseTopActivity(result.output)
        return target.successResult {
            put("packageName", top?.packageName)
            put("activity", top?.activity)
        }
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
internal class OpenUrlCommand(adb: JetWhaleAdb) : AndroidDeviceCommand(adb) {
    override val name = "$TOOL_PREFIX.openUrl"
    override val description =
        "Opens a URL or deep link with an ACTION_VIEW intent. Name the package to send it straight to " +
            "one app instead of leaving the device to pick, which a chooser dialog would otherwise stall on."

    private val url by string("The URL or deep link to open, e.g. https://example.com/orders/42 or myapp://orders/42.")
    private val packageName by stringOrNull("Restrict the intent to this app. Omit to let the device resolve it.")

    override suspend fun executeOnDevice(arguments: JetWhaleMcpArguments, target: DeviceTarget): JetWhaleMcpResult {
        val url = arguments[url]
        if (url.isBlank()) throw JetWhaleMcpArgumentException("invalid url: it is empty")
        val packageName = arguments[packageName]?.let(::requirePackageName)

        val args = buildList {
            add("am")
            add("start")
            add("-a")
            add("android.intent.action.VIEW")
            add("-d")
            add(singleQuoteForShell(url))
            if (packageName != null) {
                add("-p")
                add(singleQuoteForShell(packageName))
            }
        }
        val result = target.shell(*args.toTypedArray(), timeout = AdbTimeouts.SHELL)
        if (result.exitCode != 0 || result.output.contains("Error:")) {
            return target.failureResult(result, "am start failed")
        }
        return target.successResult {
            put("url", url)
            put("packageName", packageName)
            put("output", result.output.trim())
        }
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
internal class StartActivityCommand(adb: JetWhaleAdb) : AndroidDeviceCommand(adb) {
    override val name = "$TOOL_PREFIX.startActivity"
    override val description =
        "Builds and sends an arbitrary `am start` intent, for what launchApp and openUrl do not cover. " +
            "Give at least an action or a component; every value is quoted for the device's shell. " +
            "Extras are sent as strings (--es)."

    private val action by stringOrNull("Intent action, e.g. android.intent.action.SEND.")
    private val component by stringOrNull("Target component as package/activity, e.g. com.example.qa.sample/.MainActivity.")
    private val dataUri by stringOrNull("Intent data URI (the -d argument).")
    private val extras by stringMapOrNull("String extras to attach, as {\"key\": \"value\"}. Each becomes --es key value.")

    override suspend fun executeOnDevice(arguments: JetWhaleMcpArguments, target: DeviceTarget): JetWhaleMcpResult {
        val action = arguments[action]
        val component = arguments[component]
        val dataUri = arguments[dataUri]
        val extras = arguments[extras].orEmpty()
        if (action == null && component == null) throw JetWhaleMcpArgumentException("give at least one of action or component; an intent with neither matches nothing")

        val args = buildList {
            add("am")
            add("start")
            if (action != null) {
                add("-a")
                add(singleQuoteForShell(action))
            }
            if (component != null) {
                add("-n")
                add(singleQuoteForShell(component))
            }
            if (dataUri != null) {
                add("-d")
                add(singleQuoteForShell(dataUri))
            }
            extras.forEach { (key, value) ->
                add("--es")
                add(singleQuoteForShell(key))
                add(singleQuoteForShell(value))
            }
        }
        val result = target.shell(*args.toTypedArray(), timeout = AdbTimeouts.SHELL)
        if (result.exitCode != 0 || result.output.contains("Error:")) {
            return target.failureResult(result, "am start failed")
        }
        return target.successResult {
            put("action", action)
            put("component", component)
            put("dataUri", dataUri)
            put("output", result.output.trim())
        }
    }
}
