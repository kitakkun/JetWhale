# Storage access rules — design

The Storage Inspector lets the host, and any AI agent connected to the host's MCP server, read
everything below the app's file roots and every entry of its key-value stores. This design lets an
app declare rules that keep chosen files, directories and keys away from the host altogether, or
away from MCP clients only. The rules work like the Network Inspector's redaction rules: the app
declares them, `EVERYWHERE` and `MCP_ONLY` mean the same thing, and anything that cannot be decided
is hidden.

In this document, *the agent* is the JetWhale agent inside the app, and an *MCP client* is an AI
agent connected to the host. Implementation follows [#279](https://github.com/kitakkun/JetWhale/pull/279)
(writes into the app) and [#280](https://github.com/kitakkun/JetWhale/pull/280) (ZIP download).

## Where things stand

The agent answers each request from the host afresh; nothing is pushed:

- **Locations.** File roots and key-value stores, asked for again on every request.
  `platformDefaults()` gives Android's Data, Files, Cache and External cache directories, Apple's
  Home, Documents, Caches and Temporary, and the JVM's working and temporary directories. The stores
  are every SharedPreferences file, the `NSUserDefaults` domain, or `localStorage` and
  `sessionStorage`. The DataStore adapter adds a `DataStore<Preferences>` as a store.
- **Files.** Listing (name, size, times, access, link target), reads in pages of at most 1 MB, and
  deletes. Measuring a directory and hashing a file are built on the same requests.
- **Key-value stores.** Every entry with its value as text, and removing one key.

The host's MCP tools mirror these: `listLocations`, `listDirectory`, `readFile`, `hashFile`,
`measureDirectory`, `deleteFileEntry`, `readKeyValueStore` and `removeKeyValue`, each prefixed with
`com.kitakkun.jetwhale.storage.`. `readFile` also decodes a whole `*.preferences_pb` file into its
entries. #279 adds `WriteFileChunk` and a `writeFile` tool. #280 adds **Download as ZIP…** to the UI
only, built on `listDirectory` and whole-file reads.

So an MCP client can read, for example:
- Android's `shared_prefs/*.xml` and `files/datastore/*.preferences_pb`, where apps keep access and
  refresh tokens;
- SQLite databases under `databases/` with their `-wal` files, and WebView's cookie database under
  `app_webview/`;
- the `NSUserDefaults` plist under `Library/Preferences/`, and anything the app wrote to Documents;
- the same values through `jetwhale.screenshot` and `jetwhale.getAccessibilityTree` captures of the
  Storage UI.

The Android Keystore and the iOS Keychain are not files below any root, so they are out of reach
either way.

The user's only lever is `McpPermissions`, which allows or denies each tool as a whole. Denying
`readFile` hides every file; nothing hides one file while the tool stays useful.

## Goals

- **App-declared.** The rules live in the app's code next to the plugin's registration, so every
  developer and every host gets the same ones.
- **Two scopes, as in Network.** `EVERYWHERE`: the content never leaves the app through this plugin.
  `MCP_ONLY`: the developer sees it in the host window, and MCP clients do not.
- **One rule covers every way in.** Listings, reads, hashes, measurements, writes, deletes, ZIP
  downloads, previews and MCP captures all go through the same decision.
- **Visible, not silent.** By default a hidden item is still listed, with a marker that says it is
  hidden and by which rule, so neither a person nor an MCP client mistakes it for a missing file.
- **Fail closed.** A path that cannot be resolved, a rule that cannot be placed, a peer that does
  not know the rules, or a bulk operation that would reach a hidden item, all end with less shown,
  never more.

## Non-goals

- **A security boundary against the developer.** Whoever builds the app can remove the rules, and
  adb, `run-as`, Xcode's container download and device backups reach the same files. The rules keep
  secrets out of the host, its screenshots and MCP context, not out of the developer's reach.
- **Protection from a modified host.** `MCP_ONLY` trusts the host to say which requests come from
  MCP, as Network's `MCP_ONLY` trusts the host to apply its rules.
- **Other channels.** A token in an HTTP header is the Network Inspector's to redact. The app's own
  logs, Device Mirror frames and other plugins are out of scope.
- **Rules inside a file.** A rule hides a whole file, not a JSON field in it or a column of a
  database. A Proto DataStore file is opaque bytes, so only a path rule reaches it.
- **Rules set in the host.** The user's control stays `McpPermissions`; see *Open questions*.

## Rules

### Path rules

A path rule names a root and one or more patterns matched against the path below it, with `/`
between segments on every platform:

- `*` matches any run of characters within one segment, and `?` matches one character.
- `**` as a whole segment matches zero or more segments.
- Patterns are anchored at the root: `*.db` matches only at the top, and `**/*.db` matches at any
  depth.
- A rule that matches a directory hides everything below it, so `secrets` and `secrets/**` hide the
  same items. A descendant cannot be shown again by a narrower rule.
- An empty segment, `.`, `..`, `\` or a leading `/` is refused when the rules are built, with an
  `IllegalArgumentException`.

Matching ignores case and compares Unicode in NFC, on every platform. On a case-sensitive file
system this can hide `Token.json` alongside `token.json`. That over-hides, and never under-hides
(see *Path spelling*).

### Key-value rules

A key-value rule names a store and one or more key patterns, with `*` and `?` as above. The store
name is a pattern too, so `"*"` covers every store. Both match whole names and ignore case.

A store's entries also sit in its file: SharedPreferences in `shared_prefs/<name>.xml`,
`NSUserDefaults` in `Library/Preferences/<bundle id>.plist`, and a DataStore in its
`*.preferences_pb`. A key rule would mean nothing while that file is readable, so a store that has
a key rule also has its backing file hidden, with the key rule's scope. Files beside it whose names
start with its name are hidden too, such as `settings.xml.bak` and `settings.preferences_pb.tmp`.
The Key-Value view is then the way to see that store, without its hidden keys.

### Scopes

`HidingScope` has the values and meanings of Network's `RedactionScope`:

- **`EVERYWHERE`** (the DSL's default): the agent withholds the item from every request. Its content
  never leaves the app through this plugin.
- **`MCP_ONLY`**: the host window shows the item, and MCP tool results and MCP captures do not.

### Placeholder or omission

`HidingStrategy` decides what a listing shows of a hidden item:

- **`PLACEHOLDER`** (the default): the name is listed, marked with the rule, and nothing else about
  the item is.
- **`OMIT`**: the item is left out of listings and store contents entirely.

The trade-off is between honesty and the name:

- A placeholder tells the reader that something is there and why. An MCP client does not conclude
  that `session.json` is missing, that the user is signed out, or that it should recreate the file.
  A refused delete or an unexpected measurement explains itself.
- Some names carry data, though: `user-1234@example.com.db`, a per-account directory, or a cache
  file named after a URL with a token in it. `OMIT` keeps such names out.
- `OMIT` hides names, not existence. A directory that looks empty but cannot be deleted still says
  that something is in it. An omitted view is incomplete without saying so, which can mislead an
  MCP client.

Recommendation: `PLACEHOLDER` by default, and `OMIT` for names that carry data.

## Where the rules are enforced

The agent decides every item, for both scopes. Every request that names a path or a store carries
a `requestOrigin`, either `HOST_UI` or `MCP`.

| Scope | Request from `MCP` | Request from `HOST_UI` | MCP capture of the host window |
|---|---|---|---|
| `EVERYWHERE` | withheld by the agent | withheld by the agent | nothing to show |
| `MCP_ONLY` | withheld by the agent | sent, marked with the rule | the host renders the marked item as hidden |

The host does not match rules itself. It obeys the marks the agent sends, and only for the one case
the agent cannot see: an MCP capture of what the host window has already loaded.

**Why not the way Network does it.** Network's host fetches the `MCP_ONLY` rules with
`GetRedactionConfig` on prepare and applies them when it serves MCP results. That fits Network,
because traffic is pushed to the host when it is captured, and an MCP tool reads what the host
already holds. Storage reads every item from the agent on demand, so the request can say who is
asking. Only the agent can tell what a path really is:
- where a link or junction leads;
- whether `Data:files/a` and `Files:a` are the same file;
- whether `Token.json` opens `token.json` on this device's file system.

A host matching root-relative paths would miss all three.

**`McpPermissions` and the rules compose; neither widens the other.**
- `McpPermissions` is set by the user on the host, per tool, and decides whether a call runs at
  all. `PluginInspect` and `PluginInteract` cover captures and UI input.
- The rules are set by the app, per path and per key, and decide what a call that runs may see or
  change.
- A denied tool hides everything. Allowed tools still see nothing the rules hide. The host cannot
  relax a rule, and the app cannot grant a tool.

Input sent through `PluginInteract`, such as clicks, acts as the user, so its requests carry
`HOST_UI`. An MCP client can therefore click **Delete** on an item that is hidden from MCP only. It
cannot read the item, because captures withhold it. See *Open questions*.

## What a hidden item looks like

Here, *hidden* means hidden from the request at hand: from every request under `EVERYWHERE`, and
from `MCP` requests under `MCP_ONLY`. A `HOST_UI` request gets an `MCP_ONLY` item in full, marked
with its rule.

**In a listing,** a hidden entry keeps its `name` and `isDirectory`. Every other field takes its
neutral value: size 0, no times, not a link, no link target, not readable or writable. The entry
carries `hidingRule`:

```json
{
  "name": "session.json", "isDirectory": false, "sizeBytes": 0, "linkTarget": null, "…": "…",
  "hidingRule": { "type": "path", "rootName": "Files", "pattern": "session.json",
                  "scope": "EVERYWHERE", "strategy": "PLACEHOLDER" }
}
```

The UI shows the name with *Hidden by the app · Files: session.json*. An item hidden from MCP only
shows normally in the window, with a small *Hidden from AI agents* badge.

**A request that names a hidden item** is answered with `hidingRule` and no content, whatever the
request is:
- a read returns no bytes, so `hashFile` fails with the same answer;
- listing a hidden directory returns no entries, so nothing below it ever appears;
- measuring, deleting and writing are refused.

The check runs before the item is read, listed or changed, and the answer does not depend on
whether the item exists. An omitted item is therefore not revealed by asking for it by name.

**A key-value entry** keeps its `key` and `type`. Its `value` is `<hidden>`, and the entry carries
`hidingRule`. Removing it is refused.

**Derived values.** Everything derived from content comes from content the rules let through. That
covers a file's kind and line count, decoded preferences, SHA-256, sizes, and measurement totals.
In an MCP capture, a marked item renders as it would in an MCP result: the name and the reason, with
no preview, details, hash or status line about its content. The value itself is replaced, not just
painted over, because the accessibility tree hands over the string.

## Writes and bulk operations

- **`WriteFileChunk`** (#279) is refused when its target is hidden for the request's origin, whether
  the target exists or not. For a new file, the target is the parent's resolved path plus the name.
  The check runs on the first chunk, before the staging file is created.
- **`DeleteFileEntry`** walks the subtree before deleting anything, as the recursive delete does, and
  refuses the whole delete if any entry in it is hidden. A delete never destroys what the requester
  cannot see, and the refusal comes before anything is deleted.
- **`RemoveKeyValue`** on a hidden key is refused.
- **`MeasureDirectory`** counts what a listing shows. It does not descend into a hidden directory, a
  placeholder entry counts in `withheldEntryCount` with no size, and an omitted entry is not
  counted. The totals are then a floor, as they are when `truncated` is set.
- **Save…** has no button for a hidden file, and its read would be refused anyway.
- **Download as ZIP…** (#280) walks with `HOST_UI` origin. An entry marked `EVERYWHERE` arrives
  without content, so the ZIP leaves it out and adds `jetwhale-withheld.txt` at its top, listing
  each one with its rule. The status line says how many were withheld. A withheld item is not a read
  failure, and real failures still stop the download as #280 does. Items hidden from MCP only are
  included, because the download is for the person who clicked it.

## The DSL

```kotlin
val storageAccessRules = StorageAccessRules {
    hidePath(rootName = "Files", "session.json", "secrets")
    hidePath(rootName = "Data", "databases/auth.db*", scope = HidingScope.MCP_ONLY)
    hidePath(rootName = "Documents", "accounts/*", strategy = HidingStrategy.OMIT)
    hideKeyValue(storeName = "auth", "access_token", "refresh_token")
    hideKeyValue(storeName = "*", "*token*", scope = HidingScope.MCP_ONLY)
}

startJetWhale {
    plugins { register(JetWhaleStorageAgentPlugin.platformDefaults(storageAccessRules)) }
}
```

```kotlin
class StorageAccessRules private constructor(
    val pathHidingRules: List<PathHidingRule>,
    val keyValueHidingRules: List<KeyValueHidingRule>,
) {
    class Builder internal constructor() {
        fun hidePath(rootName: String, vararg pathPatterns: String, scope: HidingScope = EVERYWHERE, strategy: HidingStrategy = PLACEHOLDER)
        fun hideKeyValue(storeName: String, vararg keyPatterns: String, scope: HidingScope = EVERYWHERE, strategy: HidingStrategy = PLACEHOLDER)
    }

    companion object {
        val None: StorageAccessRules
    }
}

fun StorageAccessRules(configure: StorageAccessRules.Builder.() -> Unit): StorageAccessRules
```

- `scope` and `strategy` default for the same reason as in Network: the DSL's common case is to hide
  everywhere, behind a placeholder, and that is also the strictest choice.
- `JetWhaleStorageAgentPlugin` and `platformDefaults` take `storageAccessRules` without a default.
  Storage holds secrets in most apps, so the choice is made where the plugin is set up, and an app
  without rules passes `StorageAccessRules.None`.
- `KeyValueStore` gains `backingFilePaths`: the files that hold its entries, empty when there are
  none, as for `localStorage`. The platform stores fill it in.
- `KeyValueStore.dataStore(name, dataStore, filePath)` takes the store's file. A `DataStore` does
  not expose it, and the app knows it from `preferencesDataStoreFile(name)` or `createWithPath`.

These are breaking changes to the agent's public API, stated in the implementing PR.

## Class design

Rules are data, the decision is a policy class, and I/O stays with the agent plugin, following
`data-and-behavior.md`.

**Protocol (data, wire-visible):**
- `HidingScope`, `HidingStrategy` and `RequestOrigin` (enums).
- `sealed interface HidingRule` with `PathHidingRule(rootName, pattern, scope, strategy)` and
  `KeyValueHidingRule(storeName, keyPattern, scope, strategy)`.
- The `HIDDEN_VALUE_PLACEHOLDER` constant, `"<hidden>"`.

**Agent:**
- `StorageAccessRules` holds the rules and builds them. Pattern validation is parsing its own
  representation, so it stays with the type.
- `StorageLayout` (data) is a request's roots and stores, each with its resolved path and its
  stores' resolved backing files. The agent plugin builds one per request, because both lists are
  asked for afresh. Resolving paths is I/O through the existing `expect` primitives.
- `StorageAccessPolicy(storageAccessRules, storageLayout)` is pure and decides:

  ```kotlin
  fun visibilityOfPath(path: LocatedPath, requestOrigin: RequestOrigin): StorageItemVisibility
  fun visibilityOfKey(storeName: String, key: String, requestOrigin: RequestOrigin): StorageItemVisibility

  data class LocatedPath(val lexicalPath: String, val resolvedPath: String?)

  sealed interface StorageItemVisibility {
      data object Shown : StorageItemVisibility
      data class Marked(val hidingRule: HidingRule) : StorageItemVisibility // MCP_ONLY, asked by HOST_UI
      data class Hidden(val hidingRule: HidingRule) : StorageItemVisibility
  }
  ```

- `JetWhaleStorageAgentPlugin` holds `storageAccessRules` and builds the layout and the policy for
  each request. It asks the policy before it reads, lists or changes a requested path, and for each
  listed entry before it is sent.

Unlike Network's `List<RedactionRule>.redact` extensions, the decision has a class of its own: one
place to test and to change.

**Host:**
- Nothing on the host holds the rules.
- The `StorageClient` implementation takes its `RequestOrigin` in its constructor. The host plugin
  builds two clients: the MCP commands get the `MCP` one, and `StorageBrowser` the `HOST_UI` one.
  A command cannot pick the wrong origin, because it never sees the other client.
- `HiddenItemDisplay(isMcpCapture)` decides what the UI renders of an item with a `hidingRule`. The
  composables build it from `LocalIsMcpCapture`, and every preview, detail and status line about an
  item goes through it.

## Wire format

Every change adds a JSON field or a type:

| Message | Addition |
|---|---|
| `ListDirectory`, `ReadFile`, `WriteFileChunk`, `DeleteFileEntry`, `MeasureDirectory`, `ReadKeyValueStore`, `RemoveKeyValue` | `requestOrigin: RequestOrigin`, required |
| `FileEntry`, `KeyValueEntry` | `hidingRule: HidingRule?` |
| `DirectoryListing`, `FileContent`, `StorageOperationResult` | `hidingRule: HidingRule?`, set when the request named a hidden item |
| `DirectoryMeasurement` | `withheldEntryCount: Int` |

The messaging format ignores unknown keys and encodes defaults:

- Reply fields default to `null` and `0`, so a new host decodes an older agent's replies. An older
  agent has no rules, so there is nothing to mark.
- `requestOrigin` has no default. A request without it fails to decode, and the agent answers with
  a failure rather than guessing the origin.
- The agent's `pluginVersion` goes to `1.1.0`, and the host manifest's `agentVersionRange` to
  `1.0.0`–`1.1.0`. An older host, whose range ends at `1.0.0`, lists a new agent as incompatible
  and never activates the plugin, so a host that would ignore the marks never receives them.
- A new host with an older agent works as it does without rules.

The official catalog installs the host plugin version that matches the running host. An older host
therefore keeps refusing a new agent until the user updates the host.

## Edge cases

### Links and junctions

A path is hidden when its lexical path (as reached through the root) or its resolved path, or an
ancestor of either, matches a rule:

- A link to a hidden file is hidden, and its `linkTarget` is withheld.
- A link whose own path matches a rule is hidden, whatever it points to.
- Resolution uses the agent's platform primitive. That is `Path.toRealPath` on desktop JVMs, which
  [#387](https://github.com/kitakkun/JetWhale/pull/387) uses to see Windows symbolic links and
  junctions; `canonicalFile` on Android; and `stringByResolvingSymlinksInPath` on Apple.
- An existing path whose resolved form cannot be computed is hidden, such as a loop or an
  unsearchable directory. A path that does not exist yet (a write's target) is judged by its
  parent's resolved form plus its name.
- Hard links and bind mounts are not detected. Such a file is judged by the path it is reached
  through.

### Path spelling

- **Case.** macOS's default APFS volume, Windows (NTFS) and Android's emulated external storage are
  case-insensitive, while iOS and Android's internal storage are case-sensitive. Matching ignores
  case everywhere, so the agent does not need to know which kind of file system it is on.
- **Unicode.** APFS opens a name in either normalization form, so both sides are compared in NFC.
- **Windows.** `FileRoot.resolve` also refuses a segment that contains `:` (an alternate data
  stream: `token.json::$DATA` opens `token.json`) or that ends in a dot or a space, which Windows
  strips. 8.3 short names (`TOKEN~1.JSO`) are caught because `toRealPath` returns long names.

### Key-value stores and their files

- **Preferences DataStore.** Shown through the adapter with key rules. Its `*.preferences_pb` file
  is hidden as the store's backing file whenever a key rule applies to the store.
- **Proto DataStore.** Has no keys the agent can read. Only path rules reach it, and the guide says
  so.
- **SharedPreferences.** Its `.xml` and `.xml.bak` files are covered by the sibling rule.
- **`NSUserDefaults`.** Its plist is covered under the Home root, though `cfprefsd` may lag behind
  the file.
- **`localStorage` and `sessionStorage`.** Have no files.

### Overlapping and absent roots

- **Overlapping roots.** Android's Data root contains Files and Cache, and Apple's Home contains the
  rest. Each rule is placed at its root's resolved path and matched against resolved absolute
  paths, so `hidePath(rootName = "Files", "secrets")` also hides `Data:files/secrets`.
- **Absent roots.** A rule that names a root the request's root list lacks cannot be placed, and
  then the agent cannot tell which files it covers. That request therefore gets every file
  withheld, with an error that names the rule. A typo then shows at the first request, instead of
  leaking through an overlapping root.
- **Stores that do not exist yet.** A key rule on such a store is fine: the store has no entries
  and no file until it is created.

## Testing

- **Protocol.** Round trips of the new types. Replies without the new fields decode to `null` and
  `0`. A request without `requestOrigin` fails to decode.
- **Policy (common tests).**
  - Glob cases, subtree hiding, case and NFC, and pattern validation.
  - Overlapping roots, an absent root, and backing files with their siblings.
  - A table over scope × origin × strategy for each kind of request.
- **Agent file system (JVM, Apple and Windows jobs).**
  - Links in both directions, dangling and looping links, and junctions on Windows.
  - `:` and trailing-dot segments.
  - A refusal that is the same whether the item exists or not.
  - A delete refused for a hidden descendant, with nothing deleted.
  - Measurement counts.
  - Writes refused for new and existing targets.
  - No reply or error message contains an omitted name.
- **Host.**
  - A fake agent records each MCP command's `requestOrigin`. `hashFile` and `readFile` on a hidden
    file return the rule, with no hash and no decoded preferences.
  - With `LocalIsMcpCapture`, marked entries, previews, details, the SHA-256 status and key-value
    values render hidden in the screenshot and in the accessibility tree, following
    `AccessibilityTreeRedactionTest`.
  - The ZIP leaves out withheld files and writes `jetwhale-withheld.txt`.
  - Negotiation lists agent `1.1.0` as incompatible under a `1.0.0`-only range.
- **Each enforcement point removed in turn** fails its test, as in #361 for Network.
- **By hand**, with the demo app declaring rules: drive the MCP tools and captures through the
  plugin-qa skill, and the window by hand.

## Implementation order

1. Protocol, `StorageAccessRules`, `StorageAccessPolicy`, and the agent's reads, listings and
   measurements, with the version bump on both sides.
2. The host: origin-bound clients, marks in the UI and in captures, and the guide.
3. Writes, deletes and the ZIP, after #279 and #280 have landed.

Phases 1 and 2 ship in the same release: an agent with rules needs a host that renders marks.

## Open questions

1. **Enforce `MCP_ONLY` on the agent, or send the rules to the host as Network does?**
   Recommendation: on the agent, with `requestOrigin`. Host-side matching cannot see links,
   overlapping roots or case, and this keeps one decision point. The cost is that the host must label
   origins correctly, which the origin-bound clients make structural.
2. **`PLACEHOLDER` or `OMIT` by default?** Recommendation: `PLACEHOLDER`, for the reasons under
   *Placeholder or omission*. `OMIT` stays an opt-in for names that carry data.
3. **Should a rule that names an absent root withhold every file?** Recommendation: yes. It is loud
   and fail-closed. The cost lands on rules for a root that comes and goes, such as Android's
   External cache when storage is unmounted. Revisit if an app needs such a rule.
4. **Should the DataStore adapter require the store's file?** Recommendation: yes. Without it, every
   key rule on that store can be bypassed through the file view. The alternative fallback, hiding
   every `*.preferences_pb` once any DataStore store has a rule, over-hides and is harder to explain.
5. **Should MCP input through `PluginInteract` count as `MCP` origin?** Recommendation: not in the
   first version. Captures already keep content away from MCP clients, and a user who wants clicks
   kept away too can deny Interact for Storage in `McpPermissions`. Marking MCP-injected input is a
   host-wide change that would serve every plugin, so it belongs in a design of its own.
6. **Should the host let the user add `MCP_ONLY` rules for a session?** Recommendation: not now.
   Per-tool permissions cover the coarse case. Host-side rules would need their own storage and UI,
   and they would mix the user's rules with the app's.
