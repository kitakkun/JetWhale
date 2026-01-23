package com.kitakkun.jetwhale.host.data.util

import java.io.File

/**
 * Finds the absolute path to the adb executable by checking common installation locations.
 *
 * This function checks the following locations in order:
 * 1. /usr/bin/adb
 * 2. /usr/local/bin/adb
 * 3. $HOME/Android/Sdk/platform-tools/adb
 * 4. $HOME/Library/Android/sdk/platform-tools/adb (macOS)
 * 5. $ANDROID_HOME/platform-tools/adb
 * 6. $ANDROID_SDK_ROOT/platform-tools/adb
 *
 * @return The absolute path to adb if found, otherwise "adb" as a fallback
 */
fun findAdbPath(): String {
    val homeDir = System.getProperty("user.home")
    val androidHome = System.getenv("ANDROID_HOME")
    val androidSdkRoot = System.getenv("ANDROID_SDK_ROOT")

    val candidatePaths = listOfNotNull(
        "/usr/bin/adb",
        "/usr/local/bin/adb",
        homeDir?.let { "$it/Android/Sdk/platform-tools/adb" },
        homeDir?.let { "$it/Library/Android/sdk/platform-tools/adb" },
        androidHome?.let { "$it/platform-tools/adb" },
        androidSdkRoot?.let { "$it/platform-tools/adb" },
    )

    return candidatePaths.firstOrNull { path ->
        File(path).exists() && File(path).canExecute()
    } ?: "adb" // Fallback to "adb" if not found
}
