# Swift apps: using and extending JetWhale without Kotlin

Status: **proposal**. Nothing on this page is implemented except what [What exists
today](#what-exists-today) describes. The design rests on local builds made with Kotlin 2.4.10,
Xcode 27.0 and an iOS 26.2 simulator; [What was checked](#what-was-checked) lists them.

## Goal

An iOS app with no Kotlin Multiplatform module should be able to adopt JetWhale from Swift alone:
add a Swift package, start the agent in a few lines, and get the official plugins that apply to an
iOS app. It should also be able to extend JetWhale from Swift without writing a Kotlin host plugin,
and everything it adds should be usable over MCP, as everything else in JetWhale is.

Today such an app has to create a Kotlin Multiplatform "umbrella" module of its own, build a
framework from it, and wire that into Xcode. Two plans exist for that. The packaging section of
[iOS native views](./semantics-ios-native-views.md#distribution-to-a-pure-swift-app) and
[PR #165](https://github.com/kitakkun/JetWhale/pull/165) propose a JetWhale-built XCFramework
through SwiftPM. A later decision, not written down in the repository, put that aside: Swift apps
would get a template and a guide for an umbrella they own, and JetWhale would ship no XCFramework
for now. Adoption by Swift-only teams is the goal here, and asking them to own a Gradle build is the
largest obstacle to it, so this page revisits that decision with evidence for both.

## What exists today

- The agent runtime and the official agent plugins are Kotlin Multiplatform libraries with
  `iosArm64` and `iosSimulatorArm64` targets, published as klibs. No framework is published.
- `startJetWhale { }` is a receiver-lambda DSL, a plugin is a subclass of the abstract
  `JetWhaleAgentPlugin`, and messages are `@Serializable` classes registered through reified
  `onEvent<E>` / `onRequest<REQ, R>`.
- The iOS demo builds its own umbrella: `demo/shared` exports nothing, links a static framework
  through `XCFramework("shared")`, and calls `installJetWhaleSemanticsProbe()` from
  `cmpAppViewController()`.
- On iOS the Compose Semantics Inspector reads UIKit, SwiftUI and Compose through the accessibility
  tree ([iOS native views](./semantics-ios-native-views.md)), Storage Inspector reads
  `NSUserDefaults` and the sandbox directories, and Debug Actions has no platform code.
- Network Inspector has a transport-agnostic core (`recordRequest`, `recordResponse`,
  `recordFailure`, `findMock`) and two adapters, Ktor (every target, iOS included) and OkHttp
  (Android, JVM). Traffic an app sends through `URLSession` directly is not captured.
- PR #165 proposes a Swift SDK: a Swift start API, a `SwiftPluginBridge` for Swift-written
  plugins, a raw-dispatch fallback in the protocol module, and a schema shared by both languages. It
  is open. This page keeps its plugin bridge and raw dispatch and proposes something else for the
  rest; see [Open decisions](#open-decisions).

## What was checked

All in scratch worktrees; nothing below is committed. Builds are debug only, on an Apple-silicon
Mac that was running other builds and simulators at the same time, so the times are rough.

| Check | Result |
|---|---|
| XCFramework of runtime + agent SDK + protocol + Network core + Compose Semantics + Storage + Debug Actions (`export(...)` of each, dynamic, `XCFramework("JetWhaleKotlin")`) | 1 min 43 s with every module's klib built for both targets, 37 s when only the two links run. All seven modules link together without conflicts. |
| Slice size (debug) | 54.4 MB device, 54.8 MB simulator; 36.0 / 36.2 MB after `strip -x -S`; 12.6 / 12.9 MB gzipped. dSYMs 42 MB per slice. The XCFramework is 190 MB on disk, 45.9 MB zipped. Release links were not run. |
| Same without Compose Semantics | 15.1 MB simulator slice, 8.7 MB stripped, 2.7 MB gzipped. The semantics agent's `api` dependency on Compose UI accounts for about 72% of the binary. |
| Generated header | 23,218 lines, 541 classes and protocols, 261 of them from Compose UI and Skiko (7,521 lines without the semantics agent). |
| SKIE 0.10.15 on the same module | Builds; with `produceDistributableFramework()` the slice is 57.8 MB and carries a `.swiftinterface`. Enabling default-argument interop for every package failed to link (undefined `…__Skie_DefaultArguments__…` symbols in Compose classes); scoped to `com.kitakkun.jetwhale` it works. 40 s link-only build. |
| Swift export (Kotlin 2.4.10) on the runtime and Debug Actions | Fails on JetWhale's own API, twice (see [the comparison](#swift-facing-api-objc-export-skie-or-swift-export)). On a small Kotlin facade over the same modules it works: 38 s, a 184 MB static library and compiled `.swiftmodule`s. |
| Pure SwiftUI app, no KMP, consuming the XCFramework as a local SwiftPM `binaryTarget` | Builds in 13 s (Swift 6 language mode). On a new iOS 26.2 simulator it connects to a headless sandbox host; `jetwhale.listSessions` lists it with three plugins. Debug app: 55 MB, nearly all of it the 54.8 MB framework. |
| Official plugins end to end from that app, over MCP | Compose Semantics: `findNodes` finds the SwiftUI `Button` by `.accessibilityIdentifier`, and two `performNodeAction` `Click`s raise the counter it drives to 2. Debug Actions declared in Swift: `listActions` shows them, `runAction` returns `count=2`, resets the counter, then `count=0`. Storage: `readKeyValueStore` returns the app's `UserDefaults` entry. |
| The same app against the SKIE framework, and against the Swift export output | Both connect and run the Swift-declared action over MCP. |
| Release exclusion | With every JetWhale call under `#if DEBUG` in the app, a Release build still embeds the framework and keeps its load command. With the recipe under [Distribution](#keeping-it-out-of-release-builds), the Release app is 128 KB, weak-links a framework it no longer contains, and launches; a Release-based Staging configuration keeps the framework and runs. Package targets see `DEBUG` only in a configuration named Debug: a "Staging" configuration did not get it, whether based on Debug or Release. |
| A second Kotlin framework in the same app | Both load and work. A plugin class from the second framework cannot be passed to the first one's `register(plugin:)`. With the second framework exporting the JetWhale modules instead, the same Swift layer compiles unchanged behind a one-line shim module. See [Kotlin/Native constraints](#kotlin-native-constraints). |

The commands, with a scratch module named `swift-check`:

```shell
# The XCFramework
./gradlew :swift-check:assembleJetWhaleKotlinDebugXCFramework

# A sandbox host: scratch app data with adb_auto_port_mapping_enabled = false, and the Debug Actions,
# Compose Semantics and Storage host plugin jars in one directory
java -Djetwhale.appDataDir=<scratch> -cp <host runtime classpath> com.kitakkun.jetwhale.host.MainKt \
  --headless --plugin-dir <jars> --server-port 5191 --wss-port 5491 --mcp-server-port 7191 \
  --mcp-allow-all-permissions

# The app, on a simulator created for the check and deleted after it
xcrun simctl create "JetWhale Swift Check" com.apple.CoreSimulator.SimDeviceType.iPhone-17 \
  com.apple.CoreSimulator.SimRuntime.iOS-26-2
xcodebuild -scheme SwiftCheckApp -configuration Debug -destination id=<udid> CODE_SIGNING_ALLOWED=NO build

# Swift export, with the variables an Xcode build would set
CONFIGURATION=Debug SDK_NAME=iphonesimulator27.0 ARCHS=arm64 TARGET_BUILD_DIR=<out> \
  BUILT_PRODUCTS_DIR=<out> FRAMEWORKS_FOLDER_PATH=App.app/Frameworks \
  DEPLOYMENT_TARGET_SETTING_NAME=IPHONEOS_DEPLOYMENT_TARGET IPHONEOS_DEPLOYMENT_TARGET=17.0 \
  ./gradlew :swift-check:embedSwiftExportForXcode
```

Over MCP: `jetwhale.listSessions`, `com.kitakkun.jetwhale.semantics.findNodes` with
`testTag: "increment-button"`, `com.kitakkun.jetwhale.semantics.performNodeAction` with `Click`,
`com.kitakkun.jetwhale.actions.listActions` / `runAction`, and
`com.kitakkun.jetwhale.storage.readKeyValueStore` with `store: "NSUserDefaults"`.

## Options

### 1. A prebuilt XCFramework through SwiftPM (proposed)

JetWhale ships `JetWhaleKotlin.xcframework`, built from a new Kotlin module, as a SwiftPM
`binaryTarget`, next to a `JetWhale` Swift source target that apps import. The framework contains
the runtime and the agent sides that apply to an iOS app: Debug Actions, Compose Semantics, Storage,
the Network core, and Custom Panels (below). The Swift target adds a `URLSession` adapter for the
Network core.

The checks show the shape works as it is: a SwiftUI app with no Kotlin connects, and three official
plugins work from it over MCP. What stands in the way is the surface Swift sees, not the binary:

- **Names a Swift developer would not guess.** Top-level functions sit on `<File>Kt` classes
  (`JetWhaleServiceDslKt.startJetWhale(configure:)`,
  `IosSemanticsProbeKt.installJetWhaleSemanticsProbe(enableSimulatorApplicationAccessibility:)`),
  factories on `.companion` (`JetWhaleStorageAgentPlugin.companion.platformDefaults()`), and Objective-C
  rules rename members (`newTransactionId()` becomes `doNewTransactionId()`, `description` becomes
  `description_`).
- **No default arguments.** `JetWhaleNetworkAgentPlugin()` is unavailable; Swift has to pass
  `redaction`.
- **Reified functions that compile and then misbehave.** `onEvent(handler:)`, `onRequest(handler:)`
  and `DebugActionsBuilder.action(title:configure:)` are exported with their type parameter erased.
  The argument-less action Swift actually needs is `action(title:configure_:)`.
- **Suspend lambdas as protocols.** `DebugActionBuilder.perform(block:)` takes a
  `KotlinSuspendFunction1`; Swift has to write an `NSObject` subclass that implements
  `invoke(p1:) async throws -> Any?`. It works: Kotlin called the Swift `async` body and got its
  result back.
- **Internal API in plain sight.** `@InternalJetWhaleApi` members (`dispatchActivate()`,
  `bindMessenger(messenger:)`, `registerHandlers(handlers:)`, `runAction(request:)`) are in the Swift
  API; opt-in markers do not reach Objective-C.
- **Compose in the header.** The semantics agent's API exposes Compose UI types, so Skiko and
  Compose classes make up half the header, and Swift warns that
  `JWKUi_graphicsPathSegmentType` "could not be mapped".
- **Arguments from `Codable`.** An action's arguments come from a `KSerializer`, which Swift cannot
  produce. Only argument-less actions are declarable from Swift today.
- **The semantics probe crashes from `App.init`.** `installJetWhaleSemanticsProbe()` reads
  `UIApplication.sharedApplication`, which is still `nil` while SwiftUI constructs the `App`, and the
  process dies with `SIGSEGV` in `IosSemanticsProbe.Installation.<init>`. Its KDoc recommends calling
  it from the `App`'s `init`. Installing it on `UIApplication.didFinishLaunchingNotification` works.
- **Threading.** Action bodies run on a background dispatcher unless the action sets
  `runsOnMainThread`, and Kotlin objects are not `Sendable`; a Swift 6 wrapper keeps its Kotlin state
  on the main actor.

So the proposal is a binary plus two thin layers that fix the surface:

- **A Kotlin facade module** (`jetwhale-swift-facade`, `iosMain`) whose public API is shaped for
  Objective-C export: plain classes, no receiver lambdas, no reified or generic entry points, no
  default arguments, no Compose types, callbacks as `(…) -> Unit` closures rather than `suspend`
  lambdas, and one factory per official plugin so that Swift never sees a plugin class. It is the
  only module the framework exports, so the header is the facade and the few types it names.
- **A Swift layer** (the `JetWhale` target, source) that turns the facade into Swift idiom:
  `JetWhale.start { }`, `async` closures, `Codable` arguments, enums. It should stay a few hundred
  lines, with no Kotlin knowledge beyond the facade's names.

Each layer gets a check that pins it: the facade klib ABI validation like the other published
modules, and the Swift layer a CI job that compiles it against the built framework on a macOS
runner.

### 2. Extending from Swift with data (proposed)

A Swift team should not need a Kotlin host plugin to show its own state in JetWhale. Two generic
host plugins render whatever the app sends:

- **Debug Actions**, declared from Swift with `Codable` arguments. The host already builds its form
  and the MCP schema from an `ActionParameter` list; a Swift-declared action supplies that list
  itself instead of a `KSerializer`. Argument-less actions declared from Swift already work over MCP
  (checked above).
- **Custom Panels**, a new official plugin: the app pushes tables, key-value lists, JSON trees and
  event logs, and the host shows them. See [Custom Panels](#custom-panels-proposed).

Both answer MCP the way Debug Actions does now: a fixed pair of tools per plugin that lists what the
app declared and acts on it by id, because the host's MCP server fixes a connection's tool list when
it opens (`listChanged = false`).

### 3. A Swift agent side for a custom Kotlin host plugin (proposed, later)

For a team that writes a Kotlin host plugin and wants its agent side in Swift. This is #165's
`SwiftPluginBridge`: a Kotlin interface exported as an Objective-C protocol, implemented by the
Swift layer, with messages crossing as `(messageType, json)` strings. Two pieces from #165 are
needed. A raw-dispatch fallback in `JetWhaleMessageHandlers`, because inbound handlers can only be
registered through reified functions today. And #165's `RawMessenger`: the raw send methods exist
(`sendRaw`, `requestRaw`), but a plugin's `messenger` is `protected`, and `requestRaw` declares no
`@Throws`, so a timeout reaching Swift would terminate the app instead of throwing.

What it loses is the shared type contract; see [Type safety](#type-safety).

### Rejected: the agent runtime in Swift

A second runtime in Swift would remove Kotlin from the app entirely, but every official agent
(Semantics, Storage, Debug Actions, Network, and whatever comes next) would need a Swift
implementation kept in step with the Kotlin one, and the protocol would have two implementations to
keep compatible. The checks show the Kotlin binary already runs in a pure Swift app; what it lacks is
a better surface, which costs far less than a second runtime.

## Swift-facing API: ObjC export, SKIE or Swift export

Three ways to turn the Kotlin API into Swift were tried on the same modules.

**Objective-C export** is the Kotlin/Native default and is what the checks above ran on.

**SKIE** is Touchlab's compiler plugin that adds Swift code to an Objective-C-exported framework.
Version 0.10.14 added Kotlin 2.4.10 support and 0.10.15 (2026-09-25) Kotlin 2.4.20
([changelog 0.10.14](https://skie.touchlab.co/changelog/0.10.14),
[0.10.15](https://skie.touchlab.co/changelog/0.10.15)); its compatibility page states Kotlin
2.0.0 to 2.4.10 ([SKIE intro](https://skie.touchlab.co/intro)). A binary XCFramework needs Swift
library evolution, which `produceDistributableFramework()` turns on
([Swift compiler configuration](https://skie.touchlab.co/configuration/swift-compiler)).

**Swift export** is Kotlin's own Kotlin-to-Swift translation, Alpha in 2.4.x
([Kotlin docs](https://kotlinlang.org/docs/native-swift-export.html)). It is configured with
`kotlin { swiftExport { moduleName; flattenPackage; export(project) { moduleName; flattenPackage } } }`.
In 2.4.10 its Gradle tasks are registered only when Xcode's environment is present
([`registerEmbedSwiftExportTask`](https://github.com/JetBrains/kotlin/blob/v2.4.10/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/mpp/apple/AppleXcodeTasks.kt#L189)),
the binary is a static library
([`staticLib(SWIFT_EXPORT_BINARY, …)`](https://github.com/JetBrains/kotlin/blob/v2.4.10/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/mpp/apple/swiftexport/SwiftExport.kt#L199),
whose SwiftPM build step notes
[`FIXME: This will not work with dynamic libraries`](https://github.com/JetBrains/kotlin/blob/v2.4.10/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/mpp/apple/swiftexport/tasks/BuildSPMSwiftExportPackage.kt#L138)),
and `embedSwiftExportForXcode` copies that library and compiled `.swiftmodule` files, without
`.swiftinterface`s, into `BUILT_PRODUCTS_DIR`. The docs say it works "only in projects that use
direct integration".

The same declarations, as Swift sees them:

```swift
// Objective-C export
JetWhaleServiceDslKt.startJetWhale(configure: (JetWhaleConfigurationScope) -> Void) -> JetWhaleSession
IosSemanticsProbeKt.installJetWhaleSemanticsProbe(enableSimulatorApplicationAccessibility: Bool)
builder.action(title: String, configure_: (DebugActionBuilder<KotlinUnit>) -> Void)
action.perform(block: KotlinSuspendFunction1)       // Swift implements invoke(p1:) async throws -> Any?

// SKIE 0.10.15
startJetWhale(configure: @escaping (any JetWhaleConfigurationScope) -> Void) -> any JetWhaleSession
installJetWhaleSemanticsProbe()                     // default-argument overload
builder.action(title: String, configure: (DebugActionBuilder<KotlinUnit>) -> Void)
action.perform(block: KotlinSuspendFunction1)       // Swift implements __invoke(p1:) instead

// Swift export, on a facade written for it
func start(appName: String, endpoint: any SwiftEndpoint, logLevel: SwiftLogLevel)  // no default
func registerSuspendingAction(title: String, perform: @escaping () async throws -> String?)
func ping() async throws -> String
func ticks() -> any KotlinTypedFlow<Int32>
```

| | Objective-C export | SKIE | Swift export (2.4.10) |
|---|---|---|---|
| Top-level functions | On `<File>Kt` classes | Global functions | Global functions |
| Enums | Classes with static members | Swift enums | Swift enums |
| Sealed hierarchies | Class hierarchy, `as?` | `onEnum(of:)` exhaustive switch | A protocol; nested subclasses reachable only by mangled names |
| `suspend fun` | `async` | `async`, with cancellation | `async` |
| `suspend` lambda parameter | `KotlinSuspendFunction1` protocol | Same, renamed `__invoke` | `async` closure |
| `Flow` | `Kotlinx_coroutines_coreFlow` | `AsyncSequence` | `KotlinTypedFlow`, `asAsyncSequence()` |
| Default arguments | No | Opt-in; global opt-in broke the link here | No |
| Reified and generic API | Erased, compiles, misbehaves | Same | Erased to upper bounds |
| JetWhale's real API | Exports | Exports | Fails twice (below) |
| Binary through SwiftPM | XCFramework, checked | XCFramework with `.swiftinterface`, checked | No: static library and `.swiftmodule`s for the Xcode build that made them |
| Maturity | Stable, part of Kotlin | Third-party, usually a release within days of each Kotlin release | Alpha, breaking changes expected |

Swift export failed on JetWhale's own API, in ways the facade avoids but that show its state:

- `ActionDescriptor.description: String?` and `ActionParameter.description` become Swift
  properties that override `KotlinBase.description: String`, which does not compile (`property
  'description' with type 'String?' cannot override a property with type 'String'`). Objective-C
  export renames them to `description_`.
- `JetWhalePluginConfigurationScope.register(plugin: AgentPlugin)` uses an `internal typealias
  AgentPlugin = JetWhaleAgentPlugin`; the generated Swift names `AgentPlugin`, which was not
  exported (`'AgentPlugin' is not a member type`).
- Subclasses nested in a sealed interface are published through `internal` typealiases, so Swift has
  to write `_ExportedKotlinPackages_com_kitakkun_jetwhale_swiftcheck_SwiftEndpoint_Ws(...)`. The
  `sealedType()` switch the Kotlin docs describe is not in 2.4.10's output.
- The generated Kotlin bridges are compiled as part of the module, so the repository's Kotrail rules
  and `allWarningsAsErrors` reject them, and every opt-in marker in the exported API
  (`InternalJetWhaleApi`, `ExperimentalJetWhaleApi`, `kotlin.time.ExperimentalTime`) has to be
  opted into at module level, as the docs warn.
- The Gradle side needs Xcode's variables, including `DEPLOYMENT_TARGET_SETTING_NAME`, so it only
  runs inside the consumer's Xcode build, against JetWhale's Kotlin sources.

What Swift export does well is exactly where Objective-C export is weakest: `suspend` lambdas become
`async` closures, enums become Swift enums, and, per its docs, a nullable primitive stays `Int32?`
rather than becoming a boxed `KotlinInt`.

**Decision.** Now: Objective-C export, with the Kotlin facade and the hand-written Swift layer.
It is the only one of the three that is both stable and distributable as a binary, and the facade
removes most of what SKIE would fix. SKIE does not fix the hardest cases here (`suspend` lambda
parameters, erased reified functions, `Codable`), and adopting it ties every Kotlin upgrade in this
repository to a SKIE release. Later: revisit Swift export when it produces a distributable binary
and leaves Alpha. The Swift layer's public API is ours, so switching what sits under it does not
change what apps call.

## Type safety

| Path | Where the contract lives | What checks it |
|---|---|---|
| Official plugins in the XCFramework | Kotlin, on both ends | The Kotlin compiler, as today |
| Swift layer over the facade | The facade's exported signatures | The Swift compiler, against the header |
| Debug Actions with `Codable` arguments | The Swift type | Swift derives the parameter list from the type and decodes with the same type; the agent checks names and required fields against that list before calling Swift |
| Custom Panels | A fixed protocol owned by the plugin | Kotlin on both ends; Swift only fills values |
| A Swift agent side for a Kotlin host plugin | Two copies: Kotlin `@Serializable` and Swift `Codable` | Nothing, today |

The last row is where the contract is lost. The plugin payload format is
`Json { ignoreUnknownKeys = true; encodeDefaults = true }` (`DefaultJetWhaleMessagingFormat`), with
kotlinx.serialization's default `explicitNulls = true`. So:

- a key Swift misspells is ignored, and the property it meant silently takes its default;
- a nullable Kotlin property without a default must be present, even as `null`, but Swift's
  synthesized `Encodable` leaves out a `nil` optional. Swift's natural output for
  `ActionDescriptor.group == nil` is therefore a message Kotlin rejects;
- a rejected event is dropped with only a line in the receiving side's log
  (`InboundFrameDispatcher.dispatchNotification`), and a rejected request fails with the
  serialization message.

Sealed values also need a `"type"` discriminator with the subclass's serial name, `ByteArray` is a
JSON array of numbers rather than base64, and a message's type key is the payload class's
`serialName`.

Proposed, for option 3:

1. **Generate the Swift types from the protocol module.** A Gradle task loads the protocol module's
   JVM classes, takes each `JetWhaleEvent` / `JetWhaleRequest<R>` serializer's descriptor (serial
   names, element names, nullability, optional elements, enum entries, sealed subclasses) and the
   `R` type argument by reflection, and writes Swift `Codable` structs with
   `static let messageType` and a `Reply` type for requests. Their `encode(to:)` is generated too:
   it writes `nil` as `null` wherever Kotlin has no default, and the `"type"` discriminator for
   sealed types. A type it cannot map fails the task with the property's path. The Kotlin author
   keeps writing data classes.
2. **Send a contract hash.** The generator also writes a hash of the descriptors, and the agent
   sends it per plugin. `capabilities` is one session-wide `Map<String, String>`, so it needs either
   a key scheme (`contract.<pluginId>`) or a new field on `JetWhalePluginInfo`; both are wire-visible
   and have to be settled before the first release that uses them. The host warns when the hashes
   differ and decodes leniently, which keeps the cross-version tolerance `ignoreUnknownKeys` exists
   for. When they match, both sides claim the same contract, so any mismatch is a bug in one of
   them: the host decodes that plugin's messages strictly (`ignoreUnknownKeys = false`) and reports
   the message type and field path on the plugin's page and in the host log that `jetwhale.getLogs`
   reads.

A schema-first format generating both Kotlin and Swift was considered. It keeps the two sides
symmetric, but takes "write a `@Serializable` class" away from Kotlin authors, who are most plugin
authors, and adds a format to maintain.

## Custom Panels (proposed)

A new official plugin, `com.kitakkun.jetwhale.panels`, split like the others into protocol, agent
and host. The agent ships in the XCFramework and in the klibs, so Kotlin apps get it too.

A panel is one titled view of app state, of one kind:

| Kind | Content |
|---|---|
| `TABLE` | Columns (key, title) and rows of scalar cells (string, number, boolean, null) |
| `KEY_VALUE` | Ordered entries of key and scalar value, with an optional note |
| `JSON_TREE` | One JSON value, shown collapsible |
| `EVENT_LOG` | Appended entries: timestamp, level, message, optional JSON attributes; bounded by a capacity |

```kotlin
@Serializable data class PanelDescriptor(
    val id: String, val title: String, val group: String?, val kind: PanelKind, val description: String?,
)
@Serializable enum class PanelKind {
    @SerialName("table") TABLE, @SerialName("key_value") KEY_VALUE,
    @SerialName("json_tree") JSON_TREE, @SerialName("event_log") EVENT_LOG,
}
@Serializable sealed interface PanelContent {
    @SerialName("table") data class Table(val columns: List<PanelColumn>, val rows: List<List<JsonPrimitive>>) : PanelContent
    @SerialName("key_value") data class KeyValues(val entries: List<PanelEntry>) : PanelContent
    @SerialName("json_tree") data class JsonTree(val value: JsonElement) : PanelContent
    @SerialName("event_log") data class EventLog(val entries: List<PanelLogEntry>, val capacity: Int) : PanelContent
}
@Serializable data class PanelColumn(val key: String, val title: String)
@Serializable data class PanelEntry(val key: String, val value: JsonPrimitive, val note: String?)
@Serializable data class PanelLogEntry(
    val sequence: Long, val timestampMillis: Long, val level: PanelLogLevel, val message: String,
    val attributes: JsonObject?,
)
```

These types are not exported to Swift: the facade takes the content as JSON, so the protocol
module's names are free to follow Debug Actions' (`description`, not a second word for it).

Wire messages, named the way Debug Actions names its own:

| Message | Direction | Purpose |
|---|---|---|
| `panels/list_panels` → `panels/catalog` | host → agent | Every panel declared now; asked on connect |
| `panels/get_panel_content {panelId}` → `panels/panel_content {revision, content}` | host → agent | A panel's current content |
| `panels/panels_changed {catalog}` | agent → host | A panel was added or removed |
| `panels/content_changed {panelId, revision, content}` | agent → host | A table, key-value list or tree changed |
| `panels/log_appended {panelId, entries}` | agent → host | New log entries |

The agent keeps the latest content of every panel, so a host that connects late, or enables the
plugin later, asks once and is current. Content changes are coalesced per panel, so an app that
updates a table every frame sends the latest one at most every 250 ms. Log entries carry a
sequence number per panel. `log_appended` is sent with `trySend`, not buffered: after a reconnect
the host asks for the content again and gets the log's latest entries with their sequence numbers,
so nothing is counted twice, and a gap in the numbers says that entries fell out of the log's
capacity.

MCP, fixed per plugin:

| Tool | What it does |
|---|---|
| `com.kitakkun.jetwhale.panels.listPanels` | Every panel with its id, title, group and kind |
| `com.kitakkun.jetwhale.panels.readPanel` | One panel's content; `limit` and `filter` (substring over cells and log messages) for large ones |

Panels are read-only in the first version. Editing a value (a feature flag, say) is a Debug Action
the app declares next to the panel.

## Swift API shape (proposed)

```swift
#if JETWHALE
import JetWhale
#endif

@main
struct MyApp: App {
    init() {
        #if JETWHALE
        JetWhale.start { config in
            config.appName = "My App (staging)"
            config.endpoints = [.ws(host: "localhost", port: 5080), .discoverWss(allowHostNames: ["my-mac"])]
            config.trust = .serverCertificate
            config.plugins = [.semanticsInspector, .storageInspector, .networkInspector(.urlSession)]
        }
        #endif
    }
}
```

`start` returns at once and installs the semantics probe when the application has finished
launching, which avoids the `App.init` crash above. `JETWHALE` is the app's own compilation
condition, set in the configurations that should carry JetWhale; see
[Keeping it out of release builds](#keeping-it-out-of-release-builds).

Debug Actions, with arguments from a `Codable` type:

```swift
enum Tier: String, Codable, CaseIterable { case free, pro }

struct SignIn: DebugActionArguments {          // DebugActionArguments: Codable
    var email: String
    var tier: Tier?                             // optional, so the form may leave it out
    static let descriptions = ["email": "The test account's address."]
}

JetWhale.actions.register("Sign in as test user", group: "Account", arguments: SignIn.self) { args in
    try await auth.signIn(email: args.email, tier: args.tier ?? .free)
    return .text("signed in")
}

JetWhale.actions.register("Reset onboarding", destructive: true) {
    await onboarding.reset()
}

// Actions that exist while a view is shown
CheckoutView().debugActions { actions in
    actions.register("Fill test card") { await form.fill(.visa) }
}
```

The parameter list comes from running `SignIn.init(from:)` once against a decoder that records what
it is asked for: a key read with `decode` is required, with `decodeIfPresent` optional; `String`,
integer, floating-point and `Bool` reads give their input types; a `CaseIterable` `RawRepresentable`
type gives an enum with its cases; anything else is JSON. Swift's synthesized `Codable` ignores
property defaults, so "may be left out" means `Optional`.

Custom Panels:

```swift
struct FlagRow: Encodable { let flag: String; let enabled: Bool; let source: String }

JetWhale.panel("Feature Flags").table(flags.map { FlagRow(flag: $0.key, enabled: $0.isOn, source: $0.source) })
JetWhale.panel("Session").keyValues(["userId": .string(user.id), "plan": .string(user.plan.rawValue)])  // KeyValuePairs, ordered
JetWhale.panel("Remote Config").json(remoteConfig)            // any Encodable

let analytics = JetWhale.panel("Analytics").eventLog(capacity: 500)
analytics.append("screen_view", level: .info, attributes: ["screen": "Home"])
```

A table's columns are the rows' encoded keys in the order they first appear, or an explicit
`columns:` list. The first row alone would not do: synthesized `Encodable` leaves out `nil`
properties.

A custom plugin's agent side (option 3), with message types generated from the Kotlin protocol
module:

```swift
// Generated from com.example.cart.protocol
struct GetCart: JetWhaleRequest { typealias Reply = CartSnapshot; static let messageType = "cart/get" }
struct CartSnapshot: Codable { let items: [CartItem] }

final class CartPlugin: JetWhalePlugin {
    let pluginId = "com.example.cart"
    let pluginVersion = "1.0.0"

    func configure(_ handlers: JetWhaleHandlers) {
        handlers.onRequest(GetCart.self) { _ in CartSnapshot(items: cart.items) }
    }
}

JetWhale.start { config in config.plugins.append(.custom(CartPlugin())) }
```

Under these, the facade the Swift layer calls looks like this. Swift never sees the DSL, a reified
function, a `suspend` lambda or a plugin class:

```kotlin
class SwiftAgentConfiguration {
    var appName: String? = null
    var logLevel: String = "INFO"
    fun addWs(host: String, port: Int)
    fun addWss(host: String, port: Int)
    fun addDiscoverWss(hostNames: List<String>, addresses: List<String>, allowAll: Boolean)
    fun trustServerCertificate()
    fun trustCertificate(pem: String)
    fun addSemanticsInspector()
    fun addStorageInspector()
    fun addNetworkInspector(redactionRulesJson: String): SwiftNetworkCapture
    fun addDebugActions(): SwiftDebugActions
    fun addCustomPanels(): SwiftCustomPanels

    // For an umbrella app's own Kotlin plugins; kept out of the header.
    @HiddenFromObjC
    fun addPlugin(plugin: JetWhaleAgentPlugin)
}

class SwiftAgentStarter {
    fun start(configuration: SwiftAgentConfiguration): JetWhaleSession
}

class SwiftDebugActions {
    fun register(
        title: String, group: String?, description: String?, destructive: Boolean, scoped: Boolean,
        runsOnMainThread: Boolean, timeoutMillis: Long, parametersJson: String,
        options: (parameter: String, complete: (values: List<String>) -> Unit) -> Unit,
        perform: (argumentsJson: String, complete: (resultJson: String?, error: String?) -> Unit) -> (() -> Unit),
    ): DebugActionsRegistration
}
```

`perform` returns the function that cancels the run, so a `CancelActionRun` or a timeout reaches the
Swift `Task`. `JetWhaleSession` and `DebugActionsRegistration` are the only runtime types the header
names, each with one method.

## Distribution (proposed)

### Package

- **The Swift layer's source lives in this repository**, next to the facade module, so a facade
  change and the Swift change that follows it land in one PR and one CI run.
- **A thin repository publishes `Package.swift`** (name to be decided, e.g. `JetWhale-Swift`). The
  release workflow copies the Swift layer into it, with a
  `binaryTarget(name: "JetWhaleKotlin", url:checksum:)` pointing at a zip on the JetWhale GitHub
  release, and tags it with the JetWhale version. SwiftPM clones a package's whole repository, and
  this one's pack is 25 MiB and growing with screenshots; a thin repository keeps resolution fast.
- **The XCFramework is built in the release workflow** from the facade module, with a release link,
  from a clean output directory. The XCFramework task does not remove files an earlier build left
  in the framework: after a SKIE build, a plain one still carried SKIE's `.apinotes` and Swift module,
  and Swift then failed to import it. It is zipped with `ditto` and checksummed with
  `swift package compute-checksum`. dSYMs go in a separate asset: they are 42 MB per slice in a
  debug build.
- **Slices:** `ios-arm64` and `ios-arm64-simulator`, as today's targets. There is no Intel simulator
  slice, so an app building for "Any iOS Simulator" in a non-Debug configuration has to exclude
  `x86_64`, or its link fails. A `macos-arm64` slice can follow once the semantics agent has a macOS
  side; every other agent in the binary already targets `macosArm64`. The framework is dynamic, so an
  app and its extensions share one copy.
- **Minimum iOS 15**, which is what Kotlin/Native writes into the framework now (`minos 15.0`).

### Versioning against the host

The Swift package version is the JetWhale version, and the framework carries each official agent's
`pluginVersion`. The host already checks that against each host plugin's `agentVersionRange` and
negotiates the protocol version, so a Swift app meets the same compatibility rules as a Kotlin app,
with one difference: it cannot pick plugin versions individually, because they come in one binary.

### Keeping it out of release builds

SwiftPM cannot make a package product depend on the build configuration, so an app that adds the
package links and embeds the framework in every configuration. Measured: with every JetWhale call in
the app under `#if DEBUG`, the Release app still contained the framework and linked it, because the
Swift layer's own code references the Kotlin classes.

Keying the Swift layer on `DEBUG` does not fix that in general. SwiftPM defines `DEBUG` for package
targets only in a configuration named Debug: in the check, a "Staging" configuration did not get it,
whether it was based on Debug or on Release. Internal and staging builds are exactly where teams
want JetWhale, so the switch belongs to the app. The recipe, measured with Debug, Staging and
Release configurations:

1. The app sets its own compilation condition, `JETWHALE` in the sketch above, in every
   configuration that should carry JetWhale, and keeps `import JetWhale` and its calls under it.
2. In the configurations without it, `OTHER_LDFLAGS` adds `-weak_framework JetWhaleKotlin`, so the
   framework's load command becomes `LC_LOAD_WEAK_DYLIB`.
3. In those configurations, a Run Script phase deletes
   `$(TARGET_BUILD_DIR)/$(FRAMEWORKS_FOLDER_PATH)/JetWhaleKotlin.framework`. In the check, Xcode had
   embedded the package framework by the time that phase ran.

The Release app was 128 KB, contained no framework, and launched on the simulator; the Staging app
kept the framework and launched with it. A Swift layer that compiled to stubs outside `DEBUG` also
produced a 128 KB Release app, with `-Wl,-dead_strip_dylibs` in place of step 2, but it would have
switched JetWhale off in the Staging build too.

## Kotlin/Native constraints

Kotlin's documentation states that "usage of several Kotlin/Native frameworks in a Swift
application is limited" and that several modules should be exported into one umbrella framework
([Build final native binaries](https://kotlinlang.org/docs/multiplatform/multiplatform-build-native-binaries.html),
[Project configuration](https://kotlinlang.org/docs/multiplatform/multiplatform-project-configuration.html)).
Each framework contains its own runtime and every dependency, and producing one that does not is an
open request ([KT-42250](https://youtrack.jetbrains.com/issue/KT-42250),
[KT-42247](https://youtrack.jetbrains.com/issue/KT-42247)).

What that means for an app that adopts the XCFramework and later adds a KMP module of its own,
checked with a second framework (`AppShared`) next to `JetWhaleKotlin`:

- **Both load and work.** The app ran with both, connected, and read a value from each. The cost is
  a second Kotlin runtime and standard library (1.8 MB for that small debug framework).
- **Kotlin objects do not cross.** A plugin written in the app's module
  (`AppFeatureFlagsAgentPlugin : JetWhaleAgentPlugin`) cannot be registered:
  `cannot convert value of type 'AppFeatureFlagsAgentPlugin' to expected argument type
  'JetWhaleAgentPlugin'`. The two `JetWhaleAgentPlugin`s are different classes. Plain values pass
  through Swift, so an app that only feeds JetWhale from Swift is unaffected.
- **The umbrella coexists with the Swift layer.** With `AppShared` exporting the same JetWhale
  modules and the JetWhale binary target removed, a one-line Swift target named `JetWhaleKotlin`
  containing `@_exported import AppShared` let the unchanged `JetWhale` Swift layer compile against
  the app's framework, and the app registered its own Kotlin plugin through it. This works because
  exported classes keep their Swift names whatever the framework's prefix.

The check removed the binary target from a local copy of the package, which an app that depends on
the published package cannot do: the `JetWhale` product brings `JetWhaleKotlin` with it. So for apps
with Kotlin of their own, the umbrella route stays as a route of its own. The app's KMP module
depends on the facade's klib from Maven Central and exports it, and the Swift layer reaches the app
as source rather than as the package. A task in the agent Gradle plugin
(`com.kitakkun.jetwhale.agent`) copies the Swift layer into the Xcode project with
`import JetWhaleKotlin` rewritten to the umbrella's module name; the shim was the same rewrite done
by hand. The app's Swift calls stay the same. Swift export does not lift the constraint: its output
is one static library with one runtime, split into several Swift modules.

## Network over URLSession (proposed)

The Network core is exported and callable from Swift (`recordRequest(request:)`,
`recordResponse(response:)`, `recordFailure(failure:)`, `findMock(method:url:)`,
`doNewTransactionId()`), so a `URLSession` adapter needs no Kotlin change:

- A `URLProtocol` subclass in the Swift layer, inserted first into
  `URLSessionConfiguration.protocolClasses` for sessions the app creates, and registered with
  `URLProtocol.registerClass` for `URLSession.shared`, which is the only session that registration
  reaches. Sessions an SDK creates privately are covered only by swizzling
  `URLSessionConfiguration.default` / `.ephemeral`, which the adapter offers as an opt-in.
- `startLoading` records the request, asks `findMock`, and either answers with a synthesized
  `HTTPURLResponse` and body after the mock's `delayMs`, or performs the request on an inner session
  whose requests carry a `URLProtocol.setProperty` marker so the adapter does not intercept itself.
  The response is recorded with timing and a body truncated as the Ktor adapter truncates it.
- Not covered: background sessions (`URLProtocol` does not run for them), `URLSessionWebSocketTask`,
  and upload bodies given as streams, which are recorded as absent. Streaming responses are passed
  through as they arrive and recorded when complete.

## Risks

- **Size.** The debug binary is 55 MB, 72% of it Compose pulled in by the semantics agent. The
  Compose-free split of the semantics agent is a prerequisite for shipping, and release sizes have
  to be measured before the first package, since none were here.
- **The split changes artifacts.** Moving `SemanticsOwnerNodeSource` and the composable probes into a
  separate `-agent-compose` artifact means a KMP app that uses them adds that dependency. JetWhale is
  in alpha, so the PR that does it states the break rather than keeping a shim.
- **One binary, every plugin.** A Swift app cannot add or drop an official plugin without a
  different binary. One flavor keeps the release simple; more flavors multiply build time.
- **The facade drifts.** Every agent API change needs a facade change. ABI validation on the facade
  and a Swift compile in CI catch it; nothing else does.
- **Simulator-only private API.** The semantics probe's `_AXSApplicationAccessibilitySetEnabled`
  call is in the simulator slice only, so a device build, even one that ships the framework by
  mistake, does not carry it.
- **Swift export's churn.** The current Kotlin docs already describe features 2.4.10 does not
  have (`sealedType()`, Swift subclasses of Kotlin classes). Building on it now would mean following
  an Alpha API across every Kotlin release.
- **A misconfigured release build ships the whole framework.** The recipe is three steps an app has
  to take. A build-time check that warns when `JetWhaleKotlin.framework` lands in a configuration
  without the app's condition would make the mistake visible.

## Plan

1. **Fix what Swift exposes today.** The probe's `App.init` crash, and the Compose-free split of
   the semantics agent. The wire field `description` stays: the facade does not export protocol
   types, and Objective-C export already renames it.
2. **Facade and package.** The `jetwhale-swift-facade` module, the release-workflow XCFramework, the thin
   package repository, the Swift layer with `JetWhale.start`, official plugin registration and
   Debug Actions with `Codable` arguments, and a "Swift apps" guide with the release recipe.
3. **Custom Panels.** Protocol, agent, host and MCP tools, and the Swift API.
4. **`URLSession` capture** for the Network Inspector.
5. **Swift agent sides for Kotlin host plugins.** The raw-dispatch fallback and `SwiftPluginBridge`
   from #165, the Swift type generator, and the contract hash with strict decoding.
6. **Revisit Swift export** once it produces a binary that SwiftPM can distribute.

## Open decisions

1. **Ship a JetWhale XCFramework?** Recommended: yes. The checks show a Swift-only app works with
   it, and owning a Gradle build is what keeps those teams out. The umbrella stays as the
   documented route for apps that have Kotlin of their own, with the shim above.
2. **Objective-C export, SKIE or Swift export?** Decided: Objective-C export with the Kotlin
   facade and a hand-written Swift layer now, Swift export later. SKIE fixes less than the facade
   does here and couples every Kotlin upgrade to a third-party release.
3. **Where does `Package.swift` live?** Recommended: a thin repository, so SwiftPM does not clone
   this one. The alternative, a root `Package.swift` here, keeps one place to release from.
4. **Which agents go in the binary?** Recommended: Debug Actions, Compose Semantics (Compose-free),
   Storage, the Network core (its `URLSession` adapter is Swift), and Custom Panels, in one flavor.
5. **Custom Panels as a new plugin, or results of Debug Actions?** Recommended: a new plugin.
   Panels are state the app pushes when it changes; an action's result is an answer to one run.
6. **How is option 3 kept type-safe?** Recommended: generate Swift from the Kotlin protocol
   module, and decode strictly on the host when the contract hashes match. Schema-first is the
   alternative.
7. **Release exclusion: a recipe or a product?** Recommended: the app's own condition, the weak
   link and the script, documented, with a build-time warning; revisit if SwiftPM gains
   configuration-dependent products.
8. **#165.** Recommended: keep its `SwiftPluginBridge`, `RawMessenger` and raw-dispatch design for
   option 3. Let this page replace its start API and packaging sections and its schema-first
   recommendation, along with the packaging section of
   [iOS native views](./semantics-ios-native-views.md#distribution-to-a-pure-swift-app).
