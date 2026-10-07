# Changelog

Notable changes to JetWhale, for people who use it and people who write plugins for it. The format
follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

JetWhale is in alpha: any release may change APIs, the wire protocol or plugin behavior. Changes
that need action from you are marked **Breaking**. The MCP server, Debug Actions and Device Mirror
are experimental.

## [Unreleased]

## [1.0.0-alpha13] - 2026-10-07

### Added

- **The desktop host updates itself.** Settings → General → Application → **Updates** finds a newer release, downloads its host jar when you click **Download**, checks it against the release's size and SHA-256, and runs it after **Restart to Update** or at the next start. A banner at startup says when a version is available or installed; turn off **Check for updates on startup** to skip that check. A version that fails to start twice in a row is set aside and the previous one runs again, with a notice that offers **View Log** and **Try Again**. A host started with `java -jar` or `runJetWhale` doesn't update itself and links the release page instead (#394, #396, #399, #401).
- Releases carry the host release metadata, `jetwhale-host-<version>.json`, which pins each platform's host jar by size and SHA-256, and a `SHA256SUMS` file covering every release asset (#396).
- **Device Mirror** (experimental) is published for the first time: install it from **Settings → Plugins → Add Plugins → Official Plugins**, or as `com.kitakkun.jetwhale:jetwhale-device-mirror`, instead of building it from source (#341).
- Device Mirror records several devices at once: the grid's record button starts every device that can record and **Stop all** ends them, and the `startRecording` and `stopRecording` MCP tools take `all: true` or `deviceIds` (#338).
- Device Mirror takes screenshots and recordings of a physical iPhone from its video stream; both need ffmpeg (#334, #344).
- Device Mirror copies a capture to the clipboard: **Copy** in the notice that names a saved capture, and **Copy image** or **Copy file** in the Captures panel (#347).
- The Nav3 Navigator's Push pane filters the NavKey types by name, and `nav3.listNavKeyTypes` takes an optional `query` (#416).

### Changed

- **Breaking:** hosts installed before 1.0.0-alpha13 can't update themselves. Install 1.0.0-alpha13 once from its `.dmg`, `.msi` or `.deb`; your settings and plugins in `~/.jetwhale` stay, and later releases arrive through Settings → General → Application → Updates. On Linux, apt reports this first `.deb` as a downgrade from an earlier alpha's `1.0.0`; confirm it once. See [Getting Started](https://kitakkun.github.io/JetWhale/guide/getting-started#updates) (#389, #394, #399).
- The installers are now a launcher that runs the chosen host jar inside the app's own process, so on macOS the running host is the JetWhale Debugger app itself, with its Dock icon and the Local Network access you granted it, whichever host version it runs (#399).
- The `.msi` and `.deb` carry a version of their own for each release (`1.0.113` and `1.0.0~alpha13` here) instead of `1.0.0` for every alpha, so a newer installer upgrades an earlier install. The `.deb` also lists the app under Development in desktop menus (#389, #399).
- **Device Mirror** (experimental) decodes the H.264 video of Android devices and iPhones with the `ffmpeg` command installed on your machine instead of bundling ffmpeg's native libraries, so one plugin jar now works on every OS. Install ffmpeg for live video from those devices; without it an Android device is shown through screenshots. iOS simulators and emulators that expose their gRPC screen stream don't need it (#332).
- Device Mirror streams an iOS simulator at most 600 pixels wide and scales it up in the view, so a large view stays near 30 frames a second instead of about 10 (#335).
- Device Mirror draws each frame without copying it, and finds USB iPhones with `idb_companion` instead of idb's Python client, so the device list refreshes every 3 seconds instead of every 5 to 10 (#367, #368).
- Plugin installs that finish together, such as several from Official Plugins, share one notice instead of queuing one each (#340).
- Context menus, including the cut/copy/paste menu of text fields, follow the JetWhale theme instead of Compose's default light menu, in plugins too (#385).
- With **Follow the plugin an AI agent operates** on, a run of moves ends with one 2-second *Following the AI* notice instead of a 4-second notice queued per move, and host-level MCP calls (`jetwhale.navigate`, `jetwhale.setPluginEnabled`, `jetwhale.installOfficialPlugin`) no longer move the window (#420).
- MCP calls no longer hold up the host UI: `jetwhale.screenshot` stops freezing every window while it encodes, and clicks, typing and tree captures no longer recompose the plugin (#363).
- The agent does less work for each captured event, and the Storage and Compose Semantics Inspectors stop redoing work on every change and hover (#364, #365, #366, #373).

### Removed

- **Breaking:** `jetwhale.updateSettings` no longer accepts `checkForUpdatesOnStartup`, and `jetwhale.getStatus` no longer reports it. Change the setting in Settings → General → Application → Updates (#394).

### Fixed

- Android apps using R8 no longer fail with a missing `com.kitakkun.kotrail` class; the libraries ship a consumer rule for it (#333).
- On iOS and macOS, the agent's host discovery keeps its mDNS browser delegates alive, so a garbage collection during a browse can no longer crash the app (#356).
- The Network Inspector's traffic list keeps updating while a filter is typed (#360).
- The Network Inspector's traffic list has its right-click menu back: Copy as cURL, Copy URL, Copy request body and Copy response body (#384).
- The Network Inspector's traffic list no longer cuts URLs short to keep room for a mostly empty MOCK column; a mocked row shows its MOCK tag before the URL instead (#417).
- After a plugin is disabled and enabled again, its host side waits for the app to activate it before the first exchange, so plugins such as Storage and Nav3 load their state again (#422).
- The host no longer logs a TLS handshake failure, with a stack trace, each time an app connects with trust on first use (#421).
- Stopping the debug server, or restarting it to apply settings, removes the `adb reverse` mappings that ADB auto port mapping added, and quitting the host no longer leaves an `adb track-devices` process running (#419).
- On an iOS simulator, the Compose Semantics Inspector turns on application accessibility for the app it runs in, so a fresh simulator no longer shows an empty tree; pass `enableSimulatorApplicationAccessibility = false` to `installJetWhaleSemanticsProbe` to opt out. A physical device needs application accessibility turned on as the guide describes (#423).
- The Compose Semantics Inspector keeps a node's `#id` tag when the node's own text starts with `#` (#352).
- In Windows desktop apps, the Storage Inspector recognizes symbolic links and junctions, so deleting a directory from the host no longer deletes the files a link inside it points to, and a link no longer leads a listing out of its root (#387).
- The host's log viewer keeps every captured line whole and once, shows non-ASCII text intact, files java.util.logging records at their own level instead of as errors, and no longer gains about sixteen Ktor and MCP SDK entries per MCP call (#362, #374).
- The host tells you when enabling or disabling a plugin fails, and why (#355).
- The Health Check in Settings → Connection → ADB Support reports adb as missing when it isn't installed, and finds Homebrew's adb in `/opt/homebrew/bin` (#371).
- Stopping the debug server withdraws the app sessions' plugin tools from MCP instead of leaving tools that find no instance (#354).
- A plugin tool called over MCP without a `sessionId`, or with a session it isn't offered in, answers with an error that names the sessions it is available in, instead of `null` (#343).
- `jetwhale.setPluginEnabled` no longer misses a session that becomes ready while the plugin is being enabled (#351).
- Quitting the host as a plugin install finishes no longer cuts its shutdown short, and a plugin tool call or Device Mirror's **Stop all** no longer fails as the last session or recording ends (#398).
- **Device Mirror** (experimental) no longer freezes when you switch from a streaming simulator to an iPhone, and reloading the plugin no longer leaves a second `idb_companion` running (#336).
- Device Mirror follows foldable Android devices and emulators: it shows the panel that is on in its own shape, also after a fold through `adb shell cmd device_state`, turns with the screen without blinking, and returns to live video after falling back to screenshots (#348, #379).
- Device Mirror asks a physical iPhone for video that no longer breaks into blocks while the screen moves (#345).
- Device Mirror's Captures panel leaves out a capture whose sidecar can't be read, such as one deleted while the panel refreshes, instead of failing to list (#346).
- Device Mirror's missing-idb hints give one install command that works, `brew install facebook/fb/idb` (#339).
- On Windows, Device Mirror keeps the double quotes in text typed on an Android device: `say "hi"` no longer arrives as `say hi` (#388).
- When a release has no host jar for your machine (Linux on arm64, an Intel Mac, Windows on arm64), `runJetWhale` fails with a message that lists the platforms releases are built for and how to build the host instead (#390).

### Security

- Network Inspector redaction rules could be bypassed, so a value an app asked to hide reached the host, or reached MCP clients under an `MCP_ONLY` rule. `bodyField(...)` rules now withhold JSON that doesn't parse (cut at `maxBodyChars`, several documents, or malformed) when it names a redacted field; `urlQueryParam(...)` rules match percent-encoded names and hide values in failure messages; and `listTransactions`' `urlContains` matches only the redacted URL. Update the network agent in your app and the host together (#361).
- `urlQueryParam(...)` rules also hide a value quoted in a header value, such as a redirect's `Location` or a `Referer` (#412).
- Re-enabling the Network Inspector for a connected app no longer turns its `MCP_ONLY` redaction off: MCP clients received the hidden values in clear, and the Mocks tab showed no rules, until the debug server restarted. While the rules are still being read, MCP sees no transactions (#422).

## [1.0.0-alpha12] - 2026-09-28

### Added

- **Storage Inspector** plugin: browse an app's files, caches and key-value stores (SharedPreferences, `NSUserDefaults`, `localStorage`) with no app-side setup, see what each entry is and what stores it, and show live Preferences DataStores through the optional `jetwhale-storage-inspector-agent-datastore` artifact (#276, #277, #278).
- **Debug Actions** plugin (experimental): the app registers its debug menu as typed actions that a person runs from the host and an AI agent runs over MCP, with suggested argument values, a run history and confirmation for destructive actions. Its agent API requires `@OptIn(ExperimentalJetWhaleApi::class)` (#289).
- **Device Mirror** plugin (experimental): view and drive Android emulators and devices and iOS simulators inside the host (emulators stream over their own gRPC endpoint), see every device at once in a grid, see USB iPhones view-only, take screenshots and recordings, and use them from MCP tools. Not published yet; build it from source (#292, #311, #313).
- Plugins that need no app now run in an always-present host session, listed above the app picker, so they work before any app connects (#309).
- Compose Semantics Inspector on Android shows the `View` hierarchy around and inside Compose content, and reads and edits a `View`'s attributes live, also over MCP (#243, #248).
- Compose Semantics Inspector on iOS reads and drives UIKit, SwiftUI and Compose Multiplatform content through the accessibility tree (#271).
- Compose Semantics Inspector highlights the selected or hovered node on the device (#251).
- Compose Semantics Inspector reports whether a tap would actually reach each node (`hittable`, `obscuredBy`), and sums it up in one `operable` flag (#259, #261, #263).
- `BringIntoView` and `ScrollToIndex` node actions, so an agent can bring a node on screen without guessing scroll distances (#266).
- `getNodeTree` and `findNodes` take `format: "text"` for a compact outline, about 60% smaller than the JSON (#301).
- Network Inspector previews image bodies, with Copy image and Save image (#255).
- The host notices plugin jars dropped into the plugins directory while it runs and offers to load them, or to approve an update, from a banner (#303).
- Plugin installs keep running after the settings screen closes, one at a time, with progress in settings and a notice when each finishes (#319).
- The sidebar header always shows the AI agent's state: how to connect one when none is connected, and the tool being run while an agent works (#310, #318).
- The host can run inside IntelliJ IDEA as a tool window. Not published yet; build it from source with `./gradlew :jetwhale-host:idea-plugin:buildPlugin` (#273).
- The app icon is resolved automatically on Android, iOS and macOS, so the app picker shows each app's own icon (#254).
- `jetwhale-host-ui`, a published module with the host's theme and components, so plugins share one look (#247).

### Changed

- The host UI is redesigned as a desktop tool, and the built-in light and dark color schemes change (#247).
- Menus and dialogs have their own popup colors and stand out from the pane behind them (#315).
- Log times show in local time, in logcat's `MM-dd HH:mm:ss.SSS` layout (#288).
- Published jars carry `META-INF/LICENSE` and `META-INF/THIRD_PARTY_NOTICES.md` (#317).
- **Breaking:** a plugin with `requiresAgent = false` has one instance in the host session instead of one per app, `listSessions` lists `host` first, and `disposeAllPluginScenes` is replaced by `disposeAppSessionPluginScenes` (#309).
- **Breaking:** the Compose Semantics Inspector's wire format adds Android `View` nodes and iOS `apple` nodes. Host and agent must be built against the same plugin version (1.1.0), and an older host rejects the new agent (#243, #271).
- **Breaking:** `findNodes`' `hittableOnly` is replaced by `operableOnly` (#263).
- **Breaking:** a node's `actions` in MCP output lists the names `performNodeAction` accepts (`Click`), not platform names (`OnClick`) (#283).
- **Breaking:** the network redaction rule `bodyJsonField(...)` is renamed `bodyField(...)`, and it now also redacts form-urlencoded bodies (#249).
- **Breaking:** network body captures carry a `BodyEncoding` so image bodies arrive intact. Update the host and the agent together: an older host shows a new agent's image bodies as Base64 text (#255).

### Fixed

- `jetwhale.click` hits a dialog's or popup's button instead of the content underneath (#300).
- `jetwhale.type` types into the focused text field instead of the first one (#290).
- `jetwhale.navigate` refuses a disconnected session instead of opening a crashed screen (#287).
- The MCP tools session filter marks disconnected sessions (#291).
- The drawer's MCP badge no longer goes missing for a plugin that exposes tools (#281).
- The MCP permission tree is read-only, and says why, while a launch flag overrides it (#256).
- jmDNS no longer floods the host log on networks with many mDNS devices (#285).
- Apps without an icon are no longer shown with the Android icon (#286).
- Clicking empty space clears the focus ring, and focus rings are no longer clipped at the edge of a scrolling list (#250, #274).
- The host no longer leaks a cache on every recomposition of its root, or native memory when it decodes images (#257, #322).
- The follow-the-agent notice no longer makes the plugin area jump during an agent's calls (#264, #310).

## [1.0.0-alpha11] and earlier

See the [GitHub releases](https://github.com/kitakkun/JetWhale/releases).

[Unreleased]: https://github.com/kitakkun/JetWhale/compare/1.0.0-alpha13...HEAD
[1.0.0-alpha13]: https://github.com/kitakkun/JetWhale/compare/1.0.0-alpha12...1.0.0-alpha13
[1.0.0-alpha12]: https://github.com/kitakkun/JetWhale/compare/1.0.0-alpha11...1.0.0-alpha12
[1.0.0-alpha11]: https://github.com/kitakkun/JetWhale/releases/tag/1.0.0-alpha11
