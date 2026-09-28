# Changelog

Notable changes to JetWhale, for people who use it and people who write plugins for it. The format
follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

JetWhale is in alpha: any release may change APIs, the wire protocol or plugin behavior. Changes
that need action from you are marked **Breaking**. The MCP server, Debug Actions and Device Mirror
are experimental.

## [Unreleased]

## [1.0.0-alpha12] - Unreleased

### Added

- **Storage Inspector** plugin: browse an app's files, caches and key-value stores (SharedPreferences, `NSUserDefaults`, `localStorage`) with no app-side setup, see what each entry is and what stores it, and show live Preferences DataStores through the optional `jetwhale-storage-inspector-agent-datastore` artifact (#276, #277, #278).
- **Debug Actions** plugin (experimental): the app registers its debug menu as typed actions that a person runs from the host and an AI agent runs over MCP, with suggested argument values, a run history and confirmation for destructive actions. Its agent API requires `@OptIn(ExperimentalJetWhaleApi::class)` (#289).
- **Device Mirror** plugin (experimental): view and drive Android emulators and devices and iOS simulators inside the host, see USB iPhones view-only, take screenshots and recordings, and use them from MCP tools. Not published yet; build it from source (#292).
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
- The host can run inside IntelliJ IDEA as a tool window (#273).
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

[Unreleased]: https://github.com/kitakkun/JetWhale/compare/1.0.0-alpha11...HEAD
[1.0.0-alpha12]: https://github.com/kitakkun/JetWhale/compare/1.0.0-alpha11...HEAD
[1.0.0-alpha11]: https://github.com/kitakkun/JetWhale/releases/tag/1.0.0-alpha11
