package com.kitakkun.jetwhale.plugins.storage.agent

import android.app.Application
import android.content.Context
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueEntry
import java.io.File

actual fun FileRoot.Companion.platformDefaults(): List<FileRoot> {
    val context = currentApplicationOrNull() ?: return emptyList()
    return listOfNotNull(
        FileRoot(name = "Data", path = context.applicationInfo.dataDir),
        FileRoot(name = "Files", path = context.filesDir.absolutePath),
        FileRoot(name = "Cache", path = context.cacheDir.absolutePath),
        context.externalCacheDir?.let { FileRoot(name = "External cache", path = it.absolutePath) },
    )
}

actual fun KeyValueStore.Companion.platformDefaults(): List<KeyValueStore> {
    val context = currentApplicationOrNull() ?: return emptyList()
    val files = File(context.applicationInfo.dataDir, "shared_prefs").listFiles().orEmpty()
    return files
        .filter { it.name.endsWith(".xml") }
        .map { it.name.removeSuffix(".xml") }
        .sorted()
        .map { SharedPreferencesStore(context, it) }
}

private class SharedPreferencesStore(
    private val context: Context,
    override val name: String,
) : KeyValueStore {
    override suspend fun entries(): List<KeyValueEntry> = context.getSharedPreferences(name, Context.MODE_PRIVATE).all.map { (key, value) ->
        KeyValueEntry(
            key = key,
            value = if (value is Set<*>) value.joinToString() else value.toString(),
            type = when (value) {
                is Set<*> -> "Set<String>"
                null -> "null"
                else -> value::class.simpleName ?: "Unknown"
            },
        )
    }

    override suspend fun remove(key: String) {
        // commit() rather than apply(): the host re-reads the store right after, and apply() may
        // not have reached the file by then.
        check(context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().remove(key).commit()) {
            "'$key' could not be removed from $name"
        }
    }
}

/**
 * The app's [Application], reached without the app passing a Context in: the hidden
 * `ActivityThread.currentApplication()` is what the agent runtime uses for the same purpose.
 */
private fun currentApplicationOrNull(): Context? = try {
    Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as? Application
} catch (_: ReflectiveOperationException) {
    null
}
