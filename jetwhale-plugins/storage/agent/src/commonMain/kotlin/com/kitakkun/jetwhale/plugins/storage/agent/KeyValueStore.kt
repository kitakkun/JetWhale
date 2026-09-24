package com.kitakkun.jetwhale.plugins.storage.agent

import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueEntry

/**
 * A key-value store the host may read and remove entries from. Implement it to show a store the
 * platform defaults do not cover.
 */
interface KeyValueStore {
    /** Labels the store in the host, and identifies it in requests; keep it unique. */
    val name: String

    /** Every entry the store holds right now. */
    fun entries(): List<KeyValueEntry>

    /** Removes [key]; removing a key the store does not have is not an error. */
    fun remove(key: String)

    companion object
}

/**
 * The platform's preferences stores: on Android every SharedPreferences file of the app; on iOS
 * and macOS the app's `NSUserDefaults` domain; on the web `localStorage` and `sessionStorage`.
 * None on the JVM, which has no per-app store.
 */
expect fun KeyValueStore.Companion.platformDefaults(): List<KeyValueStore>
