package com.kitakkun.jetwhale.plugins.storage.host

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoredByTest {
    private val app = "/data/user/0/com.example.app"

    @Test
    fun `android storage is named after what keeps it`() {
        assertEquals("SharedPreferences", storedByOf("$app/shared_prefs/settings.xml"))
        assertEquals("SharedPreferences", storedByOf("$app/shared_prefs"))
        assertEquals("Preferences DataStore", storedByOf("$app/files/datastore/settings.preferences_pb"))
        assertEquals("Proto DataStore", storedByOf("$app/files/datastore/user.pb"))
        assertEquals("SQLite / Room", storedByOf("$app/databases/app.db"))
        assertEquals("SQLite / Room", storedByOf("$app/databases/app.db-wal"))
        assertEquals("WebView", storedByOf("$app/app_webview/Default/Cookies"))
        assertEquals("Coil image cache", storedByOf("$app/cache/image_cache/1a2b.1"))
        assertEquals("HTTP cache", storedByOf("$app/cache/http_cache/journal"))
        assertEquals("ART code cache", storedByOf("$app/code_cache"))
        assertEquals("Excluded from backup (no_backup)", storedByOf("$app/no_backup/token"))
    }

    @Test
    fun `apple storage is named after what keeps it`() {
        val home = "/var/mobile/Containers/Data/Application/1234"
        assertEquals("NSUserDefaults", storedByOf("$home/Library/Preferences/com.example.app.plist"))
        assertEquals("Caches, which the system may purge", storedByOf("$home/Library/Caches/images"))
    }

    @Test
    fun `a path nothing claims has no owner`() {
        assertNull(storedByOf("$app/files/notes.txt"))
        assertNull(storedByOf("$app/databases_backup/app.db"))
    }
}
