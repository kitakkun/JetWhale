# Storage Inspector

The Storage Inspector shows what the app you are debugging keeps on the device: the files in its
data, files and cache directories, and the entries of its key-value stores. It previews a file as
text, a hex dump, an image, or the entries of a Preferences DataStore file, and deletes a file, a
directory or a key when you want the app to start from a clean slate. It needs no configuration: the
agent finds the app's own directories and the platform's preferences store by itself.

**Works with:** Android, iOS, macOS, desktop (JVM) and the web; see the table below for what each
shows.

## Setup

### Install the host plugin

Install **Storage Inspector** from **Settings → Plugins → Add Plugins → Official Plugins**. To install
it by Maven coordinates or from a file, see [Host Settings → Plugins](/guide/host-settings#plugins).

### Add the agent to your app

```kotlin
dependencies {
    implementation("com.kitakkun.jetwhale:jetwhale-agent-runtime:<version>")
    implementation("com.kitakkun.jetwhale:jetwhale-storage-inspector-agent:<version>")
}
```

```kotlin
startJetWhale {
    connection { /* ... */ }
    plugins {
        register(JetWhaleStorageAgentPlugin.platformDefaults())
    }
}
```

`platformDefaults()` shows:

| Platform | File roots | Key-value stores |
|----------|------------|------------------|
| Android | Data (`applicationInfo.dataDir`), Files, Cache, External cache | Every SharedPreferences file in `shared_prefs/` |
| iOS / macOS | Home, Documents, Caches, Temporary | The app's `NSUserDefaults` domain |
| JVM | Working directory, Temporary | None |
| Web (JS / Wasm) | None: the browser has no file system to browse | `localStorage`, `sessionStorage` |

Android's **Data** root holds `shared_prefs/`, `databases/` and `files/datastore/`, so a DataStore
file or a SQLite database can be found there.

### Your own directories and stores

Build on the defaults to show a directory the platform does not know about, or a store of your own:

```kotlin
JetWhaleStorageAgentPlugin(
    fileRoots = { FileRoot.platformDefaults() + FileRoot(name = "Exports", path = exportDir.absolutePath) },
    keyValueStores = { KeyValueStore.platformDefaults() + MySettingsStore },
)
```

A `KeyValueStore` has a `name`, returns its `entries()`, and can `remove(key)`. Both lists are asked
for again on every request from the host, so a store the app creates after startup shows up without
registering the plugin again.

### Live DataStore stores

The file preview decodes a Preferences DataStore file from disk, which can lag behind a write the app
has just made, and cannot change it. To show a `DataStore<Preferences>` as a key-value store, add the
DataStore adapter and pass the instance your app already holds:

```kotlin
dependencies {
    implementation("com.kitakkun.jetwhale:jetwhale-storage-inspector-agent-datastore:<version>")
}
```

```kotlin
JetWhaleStorageAgentPlugin(
    fileRoots = { FileRoot.platformDefaults() },
    keyValueStores = { KeyValueStore.platformDefaults() + KeyValueStore.dataStore("settings", settingsDataStore) },
)
```

The host then reads the entries through the DataStore itself, and removing one is a
`dataStore.edit { … }`, so the app's collectors of `data` see the change. It works on every platform
the agent supports.

## Using it

**Reload from app** reads everything again, keeping the directories you have open.

### Files

A tree of every root; a directory lists its content when you open it, and Alt-click (Option-click)
opens or closes one with everything below it, stopping after 500 entries.

Selecting an entry shows its path and what keeps it when the path says so (SharedPreferences, a
DataStore, SQLite or Room, WebView, an image or HTTP cache, `NSUserDefaults`…); for a file, its kind
as its first bytes tell it and, for text, its line count; its size, times and access; and the target
of a symbolic link. **Calculate size** adds up a directory, counting links without following them; a
tree past 100,000 entries is cut off and its totals shown as "at least". **Compute SHA-256** hashes a
whole file.

The preview shows a file's first 256 KB as:

| View | For |
|---|---|
| **Preferences** | A Preferences DataStore file (`*.preferences_pb`), decoded into its keys, types and values |
| **Image** | PNG, JPEG, GIF, WebP and BMP files, such as an image loader's disk cache. **Fit** scales it to the pane; **Actual size** shows it pixel for pixel |
| **Text** | Anything that reads as UTF-8 |
| **Hex** | Everything, SQLite databases and other binary formats included |

**Save** downloads the whole file, however large. **Delete** removes a file, or a directory with
everything in it; a root itself cannot be deleted.

### Key-value stores

The stores in a list, and the selected store's entries with their types. Select an entry and press
**Delete…** to remove it.

## MCP tools

With the [MCP server](/guide/mcp-server) running, an AI agent can do the same through these tools.
Each takes the `sessionId` of the app's session.

| Tool | What it does |
|------|--------------|
| `com.kitakkun.jetwhale.storage.listLocations` | The file roots (with their absolute paths) and the key-value stores |
| `com.kitakkun.jetwhale.storage.listDirectory` | A directory's entries: size, modification and creation time, access, and link target |
| `com.kitakkun.jetwhale.storage.readFile` | A file's bytes as text or Base64, a page at a time; a whole Preferences DataStore file is also decoded |
| `com.kitakkun.jetwhale.storage.deleteFileEntry` | Delete a file or a directory |
| `com.kitakkun.jetwhale.storage.readKeyValueStore` | Every entry of a store |
| `com.kitakkun.jetwhale.storage.removeKeyValue` | Remove one entry from a store |
| `com.kitakkun.jetwhale.storage.measureDirectory` | The total size, file count and directory count below a directory |
| `com.kitakkun.jetwhale.storage.hashFile` | The SHA-256 of a whole file |

Paths are relative to a root, with `/` between segments:
`readFile(root = "Data", path = "shared_prefs/settings.xml")`.

## Limits

- **It never leaves a root.** Every path segment must be a plain name; `..`, `.` and anything with a
  separator in it are rejected by the agent before the file system is touched.
- **A read returns at most 1 MB.** A larger file is read in pages through `readFile`'s `offset`.
- **It does not guess at formats.** A Proto DataStore file or a database is shown as bytes: without
  the app's schema, a hex dump is the honest view.
