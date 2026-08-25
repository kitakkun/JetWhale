package com.kitakkun.jetwhale.plugins.androiddevice.host

/** What `dumpsys package <pkg>` says about an installed package. */
internal data class InstalledApp(
    val versionName: String?,
    val versionCode: Long?,
    val firstInstallTime: String?,
)

/**
 * `dumpsys package <pkg>`. The package is installed when the dump contains its own
 * `Package [<name>]` block; a package that is not installed produces a dump with no such block
 * (and, on most builds, an "Unable to find package" line).
 */
internal fun parseInstalledApp(output: String, packageName: String): InstalledApp? {
    if (!output.contains("Package [$packageName]")) return null
    return InstalledApp(
        // `dumpsys` prints the string "null" for a version name the manifest does not set.
        versionName = VERSION_NAME_PATTERN.find(output)?.groupValues?.get(1)?.trim()?.takeUnless { it == "null" },
        versionCode = VERSION_CODE_PATTERN.find(output)?.groupValues?.get(1)?.toLongOrNull(),
        firstInstallTime = FIRST_INSTALL_TIME_PATTERN.find(output)?.groupValues?.get(1)?.trim(),
    )
}

/** The activity the device reports as being in the foreground. */
internal data class TopActivity(
    val packageName: String,
    val activity: String,
)

/**
 * `dumpsys activity activities`. Recent builds report `topResumedActivity`; older ones only
 * `mResumedActivity`, so both are read and the first one found wins.
 */
internal fun parseTopActivity(output: String): TopActivity? {
    val record = TOP_RESUMED_PATTERN.find(output) ?: RESUMED_PATTERN.find(output) ?: return null
    return TopActivity(packageName = record.groupValues[1], activity = record.groupValues[2])
}

/**
 * `cmd package resolve-activity --brief`. The brief form prints the resolved `package/activity` on
 * its own line after a line of matching metadata; nothing resolved means no such line.
 */
internal fun parseResolvedActivity(output: String): TopActivity? {
    val line = output.lineSequence()
        .map { it.trim() }
        .lastOrNull { it.matches(COMPONENT_LINE_PATTERN) }
        ?: return null
    val (packageName, activity) = line.split('/', limit = 2)
    return TopActivity(packageName = packageName, activity = activity)
}

/**
 * `adb install`. adb exits non-zero on most failures, but some builds report `Failure [REASON]` on
 * a zero exit, so the output is read as well as the exit code.
 */
internal fun parseInstallFailure(output: String): String? {
    INSTALL_FAILURE_PATTERN.find(output)?.let { return it.groupValues[1] }
    return output.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("adb: failed to install") }
}

/** `pidof <pkg>` prints the matching pids on one line, or nothing when the app is not running. */
internal fun parsePid(output: String): Int? = output.trim().split(Regex("\\s+")).firstOrNull()?.toIntOrNull()

private val VERSION_NAME_PATTERN = Regex("versionName=(.*)")
private val VERSION_CODE_PATTERN = Regex("versionCode=(\\d+)")
private val FIRST_INSTALL_TIME_PATTERN = Regex("firstInstallTime=(.*)")
private val TOP_RESUMED_PATTERN = Regex("topResumedActivity=ActivityRecord\\{[^ ]+ [^ ]+ ([^/ ]+)/([^ }]+)")
private val RESUMED_PATTERN = Regex("mResumedActivity:? ?ActivityRecord\\{[^ ]+ [^ ]+ ([^/ ]+)/([^ }]+)")
private val COMPONENT_LINE_PATTERN = Regex("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+")
private val INSTALL_FAILURE_PATTERN = Regex("Failure \\[([^]]*)]")
