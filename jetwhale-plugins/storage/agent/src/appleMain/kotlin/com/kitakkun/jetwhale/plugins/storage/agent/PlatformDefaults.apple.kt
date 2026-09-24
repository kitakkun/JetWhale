package com.kitakkun.jetwhale.plugins.storage.agent

import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueEntry
import platform.Foundation.NSBundle
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask

actual fun FileRoot.Companion.platformDefaults(): List<FileRoot> = listOfNotNull(
    FileRoot(name = "Home", path = NSHomeDirectory()),
    searchPath(NSDocumentDirectory)?.let { FileRoot(name = "Documents", path = it) },
    searchPath(NSCachesDirectory)?.let { FileRoot(name = "Caches", path = it) },
    FileRoot(name = "Temporary", path = NSTemporaryDirectory()),
)

private fun searchPath(directory: ULong): String? = NSSearchPathForDirectoriesInDomains(directory, NSUserDomainMask, true).firstOrNull() as? String

// A process without a bundle identifier (a command-line tool, a test binary) has no defaults
// domain of its own; `standardUserDefaults` would then show only the global domains.
actual fun KeyValueStore.Companion.platformDefaults(): List<KeyValueStore> =
    listOfNotNull(NSBundle.mainBundle.bundleIdentifier?.let(::UserDefaultsStore))

private class UserDefaultsStore(private val domain: String) : KeyValueStore {
    override val name: String get() = "NSUserDefaults"

    override fun entries(): List<KeyValueEntry> {
        // The persistent domain holds what the app itself wrote; dictionaryRepresentation() would
        // mix in every global and registration default as well.
        val values = NSUserDefaults.standardUserDefaults.persistentDomainForName(domain).orEmpty()
        return values.map { (key, value) ->
            KeyValueEntry(
                key = key.toString(),
                value = when (value) {
                    is List<*> -> value.joinToString()
                    else -> value.toString()
                },
                type = when (value) {
                    is String -> "String"
                    is NSNumber -> "Number"
                    is NSDate -> "Date"
                    is NSData -> "Data"
                    is List<*> -> "Array"
                    is Map<*, *> -> "Dictionary"
                    null -> "null"
                    else -> value::class.simpleName ?: "Unknown"
                },
            )
        }
    }

    override fun remove(key: String) {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(key)
    }
}
