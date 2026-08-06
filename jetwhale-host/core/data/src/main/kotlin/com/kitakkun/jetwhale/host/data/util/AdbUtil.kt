package com.kitakkun.jetwhale.host.data.util

import java.io.File

/**
 * The absolute path to the adb executable, or the bare executable name — `adb`, or `adb.exe` on
 * Windows — to let the OS resolve it from PATH.
 *
 * `ANDROID_HOME` and `ANDROID_SDK_ROOT` are consulted first: someone who has set them has said which
 * SDK they mean, and a stray `/usr/local/bin/adb` from an unrelated install should not outrank that.
 * The rest are the conventional install locations, per OS — Windows keeps the SDK under LOCALAPPDATA
 * and names the executable `adb.exe`, so a Windows host found nothing here before and fell through to
 * PATH.
 */
fun findAdbPath(): String {
    val homeDir = System.getProperty("user.home")
    val localAppData = System.getenv("LOCALAPPDATA")
    val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
    val executable = if (isWindows) "adb.exe" else "adb"

    val sdkRoots = listOfNotNull(
        System.getenv("ANDROID_HOME"),
        System.getenv("ANDROID_SDK_ROOT"),
        homeDir?.let { "$it/Android/Sdk" },
        // macOS
        homeDir?.let { "$it/Library/Android/sdk" },
        // Windows
        localAppData?.let { "$it/Android/Sdk" },
    )

    val candidatePaths = sdkRoots.map { "$it/platform-tools/$executable" } + listOfNotNull(
        "/usr/bin/$executable".takeUnless { isWindows },
        "/usr/local/bin/$executable".takeUnless { isWindows },
    )

    return candidatePaths.firstOrNull { path ->
        File(path).let { it.exists() && it.canExecute() }
    } ?: executable
}
