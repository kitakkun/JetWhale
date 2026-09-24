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

## What you get in the host

- **Files.** A tree of every root; a directory lists its content when you open it. Selecting a file
  shows its path, size and modification time, and a preview of its first 256 KB:
  - **Preferences** for a Preferences DataStore file (`*.preferences_pb`), decoded into its keys,
    types and values.
  - **Image** for PNG, JPEG, GIF, WebP and BMP files — Coil's or Glide's disk cache, for instance.
    **Fit** scales the image to the pane, a small icon included; **Actual size** shows it pixel for
    pixel, scrolling when it is larger than the pane.
  - **Text** for anything that reads as UTF-8.
  - **Hex** for everything, including SQLite databases and other binary formats.
- **Save.** Downloads the whole file to your machine, however large — not just the previewed part.
- **Delete.** A file, or a directory with everything in it. The root itself cannot be deleted.
- **Key-Value.** The stores in a list, and the selected store's entries with their types. Each
  entry can be removed.

**Reload from app** reads everything again, keeping the directories you have open.

## MCP tools

With the [MCP server](./mcp-server) running, the same operations are available to an AI agent:

| Tool | What it does |
|------|--------------|
| `com.kitakkun.jetwhale.storage.listLocations` | The file roots (with their absolute paths) and the key-value stores |
| `com.kitakkun.jetwhale.storage.listDirectory` | A directory's entries with size and modification time |
| `com.kitakkun.jetwhale.storage.readFile` | A file's bytes as text or Base64, a page at a time; a whole Preferences DataStore file is also decoded |
| `com.kitakkun.jetwhale.storage.deleteFileEntry` | Delete a file or a directory |
| `com.kitakkun.jetwhale.storage.readKeyValueStore` | Every entry of a store |
| `com.kitakkun.jetwhale.storage.removeKeyValue` | Remove one entry from a store |

Paths are relative to a root, with `/` between segments: `readFile(root = "Data", path =
"shared_prefs/settings.xml")`.

## What it refuses to do

- **Leaving a root.** Every path segment must be a plain name; `..`, `.` and anything with a
  separator in it are rejected by the agent before the file system is touched.
- **Reading a file whole in one go.** A read returns at most 1 MB. A larger file is read in pages
  through `readFile`'s `offset`.
- **Guessing at formats.** A Proto DataStore file or a database is shown as bytes: without the
  app's schema, a hex dump is the honest view.
