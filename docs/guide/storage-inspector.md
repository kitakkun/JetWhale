# Storage Inspector

The Storage Inspector shows what the app you are debugging keeps on the device: the files in its
data, files and cache directories, and the entries of its key-value stores. It previews a file as
text, a hex dump, an image, or — for a Preferences DataStore file — the entries inside it, and it
deletes a file, a directory or a key when you want the app to start from a clean slate. An AI agent
can do the same over MCP.

It needs no configuration. The agent finds the app's own directories and the platform's
preferences store by itself.

## Setup

### Install the host plugin

The Storage Inspector is in the host's **official catalog**: open **Settings → Plugins → Add
Plugins → Official Plugins** and install it with one click. See
[Host Settings → Plugins](/guide/host-settings#plugins) for the other install routes.

### Add the agent to your app

```kotlin
dependencies {
    implementation("com.kitakkun.jetwhale:jetwhale-agent-runtime:<version>")
    implementation("com.kitakkun.jetwhale:jetwhale-storage-inspector-agent:<version>")
}
```

```kotlin
startJetWhale {
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

Android's **Data** root holds `shared_prefs/`, `databases/` and `files/datastore/`, so a
DataStore file or a SQLite database can be found there even though they are not listed separately.

#### Adding your own directories and stores

Build on the defaults to show a directory the platform does not know about, or a store of your own:

```kotlin
JetWhaleStorageAgentPlugin(
    fileRoots = { FileRoot.platformDefaults() + FileRoot(name = "Exports", path = exportDir.absolutePath) },
    keyValueStores = { KeyValueStore.platformDefaults() + MySettingsStore },
)
```

A `KeyValueStore` has a `name`, returns its `entries()`, and can `remove(key)`. Both lists are asked
for again on every request from the host, so a store the app creates after startup shows up
without registering the plugin again.

#### Live DataStore stores

The file preview decodes a Preferences DataStore file from disk, which can lag behind a write the
app has just made, and cannot change it. To show a `DataStore<Preferences>` as a key-value store,
add the DataStore adapter and pass the instance your app already holds:

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

The host then reads the entries through the DataStore itself, and removing an entry is a
`dataStore.edit { … }`, so the app's collectors of `data` see the change. It is available on every
platform the agent supports.

## What you get in the host

- **Files.** A tree of every root; a directory lists its content when you open it. Alt-click
  (Option-click) opens or closes a directory with everything below it; opening stops after 500
  entries, since each directory is another request to the app.
- **Details.** Selecting an entry shows what is known about it:
  - its path, and what keeps it when the path says so — SharedPreferences, a Preferences or Proto
    DataStore, SQLite / Room, WebView, the Coil image cache, an HTTP cache, `NSUserDefaults`,
    Caches, and so on;
  - for a file, its kind as its first bytes tell it (SQLite, PNG, JPEG, GIF, WebP, BMP, PDF, ZIP,
    gzip, JSON, XML or plain text), and for text its line count;
  - its size, when it was modified and created, whether the app can read and write it, and where
    it points when it is a symbolic link.

  **Calculate size** adds up a directory and everything below it, counting links without
  following them; a tree past 100,000 entries is cut off and its totals shown as "at least".
  **Compute SHA-256** hashes a whole file — to compare two copies, or tell whether it changed.
- **Preview.** A file's first 256 KB, as:
  - **Preferences** for a Preferences DataStore file (`*.preferences_pb`), decoded into its keys,
    types and values.
  - **Image** for PNG, JPEG, GIF, WebP and BMP files — Coil's or Glide's disk cache, for instance.
    **Fit** scales the image to the pane, a small icon included; **Actual size** shows it pixel for
    pixel, scrolling when it is larger than the pane.
  - **Text** for anything that reads as UTF-8.
  - **Hex** for everything, including SQLite databases and other binary formats.
- **Save.** Downloads the whole file to your machine, however large — not just the previewed part.
- **Upload and Replace.** **Upload…** on a directory sends a file from your machine into it;
  **Replace…** on a file overwrites it with one of yours. Replacing a file that is already there
  asks first. The file is sent in 1 MB chunks and swapped in only once all of it has arrived, so
  the app never sees it half-written.
- **Delete.** A file, or a directory with everything in it. The root itself cannot be deleted.
- **Key-Value.** The stores in a list, and the selected store's entries with their types. Select
  an entry and press **Delete…** to remove it, as with a file.

**Reload from app** reads everything again, keeping the directories you have open.

## MCP tools

With the [MCP server](./mcp-server) running, the same operations are available to an AI agent:

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
| `com.kitakkun.jetwhale.storage.writeFile` | Create or replace a file, from text or Base64 |

Paths are relative to a root, with `/` between segments: `readFile(root = "Data", path =
"shared_prefs/settings.xml")`.

::: warning Files the app keeps open
A Preferences DataStore keeps its values in memory, and a SQLite database keeps its connection and
`-wal` file open. Replacing such a file under a running app may not take effect — the app can keep
serving what it read, or write it back over yours — and a database replaced beside a stale `-wal`
can be corrupted. Restart the app after replacing one, or replace it while the app is stopped.
:::

## What it refuses to do

- **Leaving a root.** Every path segment must be a plain name; `..`, `.` and anything with a
  separator in it are rejected by the agent before the file system is touched.
- **Reading a file whole in one go.** A read returns at most 1 MB. A larger file is read in pages
  through `readFile`'s `offset`.
- **Guessing at formats.** A Proto DataStore file or a database is shown as bytes: without the
  app's schema, a hex dump is the honest view.
