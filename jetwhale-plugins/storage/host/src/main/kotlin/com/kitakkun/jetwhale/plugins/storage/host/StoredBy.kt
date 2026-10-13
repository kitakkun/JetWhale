package com.kitakkun.jetwhale.plugins.storage.host

/**
 * Where a path sits in an app's storage, matched against the directories the platform and common
 * libraries use; the first match wins, so the specific patterns come before the general ones.
 */
private val STORED_BY: List<Pair<Regex, String>> = listOf(
    Regex("/shared_prefs(/[^/]+\\.xml)?$") to "SharedPreferences",
    Regex("/datastore/[^/]+\\.preferences_pb$") to "Preferences DataStore",
    Regex("/datastore/[^/]+\\.pb$") to "Proto DataStore",
    Regex("/databases(/[^/]+\\.db(-wal|-shm|-journal)?)?$") to "SQLite / Room",
    Regex("/app_webview(/|$)") to "WebView",
    Regex("/cache/(image_cache|coil[^/]*)(/|$)") to "Coil image cache",
    Regex("/cache/(http[^/]*|okhttp[^/]*)(/|$)") to "HTTP cache",
    Regex("/code_cache(/|$)") to "ART code cache",
    Regex("/no_backup(/|$)") to "Excluded from backup (no_backup)",
    Regex("/Library/Preferences/[^/]+\\.plist$") to "NSUserDefaults",
    Regex("/Library/Caches(/|$)") to "Caches, which the system may purge",
)

/** What keeps the file or directory at [absolutePath], or null when the path matches nothing known. */
internal fun storedByOf(absolutePath: String): String? = STORED_BY.firstOrNull { (pattern, _) -> pattern.containsMatchIn(absolutePath) }?.second
