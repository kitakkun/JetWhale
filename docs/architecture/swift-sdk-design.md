# Native Swift SDK — API design

Status: **design/proposal** (not yet implemented). Packaging for a pure Swift app is covered in
[iOS native views](./semantics-ios-native-views.md#distribution-to-a-pure-swift-app); this document
covers the start API and custom Swift plugins.

## Goal

Let a **pure Swift / SwiftUI / UIKit** app (no Kotlin, no Compose) embed the JetWhale agent
idiomatically — `import JetWhale`, start it, register custom plugins, exchange messages — without
the Swift developer ever touching Kotlin idioms.

## Why a separate Swift-facing API

The existing agent API is Kotlin-idiomatic and leans on exactly the features that do **not** bridge
to Swift today (whether via Obj-C interop or the still-Alpha Swift Export):

- receiver-lambda DSLs (`startJetWhale { connection { … } }`),
- reified generics (`request<R>()`, `JetWhaleRequest<R>`), which the handler registry is built on,
- `@Serializable` message types (no `Codable` bridging),
- sealed marker interfaces,
- a plugin base class to subclass. Obj-C export carries its `protected` hooks, so a Swift subclass
  can override them, but the handler registry its `configure` receives offers only the reified
  registration above, so the subclass has no way to say which messages it handles.

So we do **not** try to export the Kotlin authoring surface. Instead we add a thin **façade layer in
`commonMain`** whose public shape is export-clean, and design a Swift wrapper on top of it. The
Kotlin DSL for Kotlin consumers stays as it is; the one change below it is a public raw-dispatch
path in `jetwhale-protocol` (see the Kotlin-side façade).

## The one design rule

> **Only strings and plain values cross the Kotlin↔Swift boundary.** All generic typing and
> serialization lives on the Swift side (`Codable`).

The sending side already has that boundary: `JetWhaleMessenger` (in `jetwhale-protocol`) is itself
the raw layer — `sendRaw(messageType: String, payload: String): Boolean` and
`suspend requestRaw(messageType, payload, timeout): String` — and the typed `trySend`/`request` are
reified extensions over it. The Swift SDK builds on the raw pair and does its own `Codable`
encode/decode, so generics and serialization never need to bridge. The receiving side has no such
boundary yet; see the Kotlin-side façade.

## Distribution

Ship as a **Swift Package with a binary `.xcframework`** target
(`.binaryTarget(name:url:checksum:)`, zip on GitHub Releases). The repo already builds an
XCFramework for the demo (the `XCFramework("shared")` block in `demo/shared/build.gradle.kts`), and
`agent-runtime` targets `iosArm64` / `iosSimulatorArm64` / `macosArm64`. The framework is
**dynamic**, for the reasons in the packaging section of the iOS native views design. Build it from
the **Obj-C interop** path today; swap to Swift Export later without changing the Swift API.

## Swift-facing API (consumer view)

```swift
import JetWhale

// 1. Start — a value config, not a receiver-lambda DSL
JetWhale.start { config in
    config.appName = "My App (staging)"
    config.logging(.info)
    config.endpoints.ws(host: "localhost", port: 5080)          // simulator
    config.endpoints.discoverWss { $0.allowHostName("my-mac") }  // physical device
    config.plugins.networkInspector()                            // official plugins
    config.plugins.register(MyPlugin())                          // your own
}

// 2. Message contracts — Swift Codable structs tagged by a stable type id
struct ButtonClicked: JetWhaleEvent {
    static let messageType = "com.example.myplugin.ButtonClicked"
    let count: Int
}
struct Ping: JetWhaleRequest {
    typealias Reply = Pong
    static let messageType = "com.example.myplugin.Ping"
}
struct Pong: Codable { let ok: Bool }

// 3. A plugin — a Swift type conforming to a protocol (no subclassing)
final class MyPlugin: JetWhalePlugin {
    let pluginId = "com.example.myplugin"
    let pluginVersion = "1.0.0"

    func configure(_ handlers: JetWhaleHandlers) {
        handlers.onEvent(ButtonClicked.self) { event in
            print("clicked \(event.count)")
        }
        handlers.onRequest(Ping.self) { _ in
            Pong(ok: true)                       // reply is the declared Reply type; may await
        }
    }

    // Lifecycle — plain callbacks; async where the Kotlin side suspends
    func onActivate(_ messenger: JetWhaleMessenger) { self.messenger = messenger }
    func onPrepare(_ messenger: JetWhaleMessenger) async throws {
        let cfg: MockConfig = try await messenger.request(GetMockConfig())
        apply(cfg)
    }
    func onDisconnected() async {}
    func onDeactivate() {}

    private var messenger: JetWhaleMessenger?
}

// 4. Sending — from anywhere
messenger.trySend(ButtonClicked(count: 1))                 // fire-and-forget
messenger.sendOrQueue(ButtonClicked(count: 2))             // buffer while offline
let pong: Pong = try await messenger.request(Ping())       // request/reply, async/await
```

Key Swift types:

- `JetWhaleEvent` / `JetWhaleRequest` — Swift protocols refining `Codable` with a `static var
  messageType: String`. `JetWhaleRequest` adds `associatedtype Reply: Codable`.
- `JetWhalePlugin` — a Swift protocol (backed by an Obj-C protocol from Kotlin) with `pluginId`,
  `pluginVersion`, `configure` and the lifecycle callbacks. `pluginVersion` is sent during session
  negotiation, as it is for every Kotlin agent plugin.
- `JetWhaleHandlers` — closure registry: `onEvent(_:_:)`, `onRequest(_:_:)`; request handlers are
  `async throws`, and a thrown error goes back to the host as the request's failure, as an exception
  from a Kotlin request handler does.
- `JetWhaleMessenger` — `trySend`, `sendOrQueue`, `sendOrFail`, and `async` `request`.
- `JetWhale.start(_:)` — trailing-closure builder over a `JetWhaleConfig` value type.

### Endpoints

The config carries an **ordered candidate list**, as `endpoints { }` does in Kotlin: the agent tries
each candidate in turn. `ws(host:port:)`, `wss(host:port:)` and `discoverWss(_:)` map one-to-one;
`discoverWss` keeps its required allowlist (`allowHostName`, `allowAddress`, `allowAll`) and still
needs `_jetwhale._tcp` under `NSBonjourServices` in the app's `Info.plist`. `buildMachineWss` is not
offered: the agent's Kotlin compiler plugin rewrites it when the *app's* Kotlin is compiled, and a
pure Swift app consumes a prebuilt framework with no Kotlin compilation of its own.

Certificate trust (`trustServerCertificate`, `trustCertificate(pem:)`) is a separate `config.ssl`
section, as `ssl { }` is in Kotlin; it does not pick the scheme, the endpoints do.

## Kotlin-side façade (what backs it)

1. **A public raw-dispatch path in `jetwhale-protocol`** — the prerequisite. Today the only public
   way to register on `JetWhaleMessageHandlers` is the reified, inline `onEvent<E>` /
   `onRequest<REQ, R>`, keyed by the serializer's `descriptor.serialName`. The non-reified
   `registerEvent` / `registerRequest` they expand to are `@PublishedApi internal` and still take a
   `KSerializer`, the constructor is `internal`, and the inbound dispatcher looks up only those typed
   entries. There is no raw shape to register a catch-all against (the QA agent's
   `WireLevelQaPlugin` is send-only for the same reason). Add a raw fallback — e.g.
   `onRawEvent { messageType, json -> }` and `onRawRequest { messageType, json -> replyJson }` — that
   the dispatcher consults when no typed entry matches. This is the one addition to the
   Kotlin-facing API.

2. **`JetWhaleSwiftConfig`** — a plain class with settable properties (`appName`, `logLevel`), an
   ordered endpoint list (`ws`, `wss`, `discoverWss`), an `ssl` section, and a `plugins` section with
   one method per official plugin plus `register(bridge)`. `JetWhale.start` (Swift) fills one and
   hands it to a non-suspend `startJetWhaleFromConfig(config)` that internally builds today's DSL.

3. **`SwiftPluginBridge`** (Kotlin `interface` → Obj-C protocol the Swift plugin conforms to):
   ```kotlin
   interface SwiftPluginBridge {
       val pluginId: String
       val pluginVersion: String
       fun onActivate(messenger: RawMessenger)
       fun onPrepare(messenger: RawMessenger, done: (failure: String?) -> Unit)
       fun onDisconnected(done: () -> Unit)
       fun onDeactivate()
       fun handleEvent(messageType: String, payloadJson: String)
       fun handleRequest(
           messageType: String,
           payloadJson: String,
           reply: (String) -> Unit,
           fail: (String) -> Unit,
       )
   }
   ```
   The members that back a Kotlin `suspend` call — `onPrepare`, `onDisconnected`, `handleRequest` —
   take callbacks rather than returning, so the Swift side can finish from an `async` context. A
   failure travels back through the callbacks too: a message passed to `onPrepare`'s `done` is
   logged and the plugin goes on unprepared, as when a Kotlin `onPrepare` throws, and `fail` becomes
   the request's failure reply, as a throwing Kotlin request handler's exception does. A Kotlin
   `SwiftBackedAgentPlugin(bridge) : JetWhaleAgentPlugin` adapts it: its `configure` registers the raw
   fallback from (1) and dispatches `(messageType, json)` to `bridge.handleEvent/handleRequest`,
   suspending until `reply` or `fail` is called; its `onPrepare` and `onDisconnected` suspend the
   same way until `done` runs, and its other hooks and `pluginVersion` forward to the bridge
   directly. The Swift side implements `SwiftPluginBridge` inside a wrapper around the developer's
   `JetWhalePlugin`.

4. **`RawMessenger`** — a narrow export of the raw messenger: `trySendRaw(type, json): Bool`,
   `sendOrQueueRaw`, `sendOrFailRaw`, and `suspend requestRaw(type, json): String`. The Swift
   `JetWhaleMessenger` wraps it and does `Codable` on both sides.

The Swift wrapper layer (in the Swift package, not Kotlin) owns: the `Codable` encode/decode, the
`messageType`→handler map, and turning `SwiftPluginBridge` callbacks into calls on the developer's
`JetWhalePlugin`.

## Wire-format compatibility (the load-bearing risk)

The host counterpart plugin deserializes messages with **kotlinx.serialization**, keyed by a **type
discriminator**. For a Swift `Codable` payload to be understood by the Kotlin host, two things must
line up:

- **`messageType`** must equal the discriminator the Kotlin side uses for that message — the
  serializer's `descriptor.serialName` (the fully-qualified class name by default, overridable with
  `@SerialName`). The Swift `static let messageType` is the explicit contract; the shared-message
  module on the Kotlin side must carry the same string (pin it with `@SerialName` so a class rename
  can't silently break the wire).
- **JSON field names/shape** must match. Swift `Codable` and kotlinx.serialization both emit plain
  JSON objects; align field names (Swift `CodingKeys` where needed) and avoid Swift-only encoding
  quirks (e.g. `Data`→base64 must match the Kotlin `ByteArray` convention).

Recommendation: define the message contract **once** as a language-neutral schema (the `messageType`
+ field list), and generate/verify both the Swift structs and the Kotlin `@Serializable` classes
against it, so the two never drift. A round-trip conformance test (encode in Swift → decode in
Kotlin and back) should gate releases.

## Async & state

- `request` / `onPrepare` → Swift `async`: Obj-C export already turns a `suspend` function into a
  method with a completion handler, which Swift imports as `async`, so no extra overloads are
  needed.
- Observable plugin state (host→agent `StateFlow`) → expose as an `AsyncStream`/`AsyncSequence`
  or a `subscribe(_:)` callback. `StateFlow.value` bridging is undocumented in Swift Export, so
  provide an explicit accessor on the façade.
- Threading: `request`/handlers run on the runtime's coroutine scope; the façade must document/main-
  thread-hop where Swift callers expect it and honor Swift task cancellation.

## Network Inspector on Swift (Phase 2)

The Network Inspector core is transport-agnostic — `JetWhaleNetworkAgentPlugin` (in
`jetwhale-plugins/network/agent`) exposes `newTransactionId`, `recordRequest`, `recordResponse`,
`recordFailure` and `findMock`, and its KDoc invites new adapters.
A Swift app uses **URLSession**, which has no global interceptor, so ship a Swift-native adapter:

- capture via a `URLProtocol` subclass (or `URLSessionTaskDelegate` + `URLSessionTaskMetrics`),
- mock-serving by having the `URLProtocol` synthesize responses from `findMock`,
- feed everything into the existing capture API — no host-side changes.

SSE/streaming and background-session parity with the Ktor adapter are the hard parts.

## Phasing

- **Phase 0** — publish `agent-runtime` (+ SDK/protocol) as an XCFramework; prove a pure-Swift app
  can call a hand-written thin Kotlin façade over Obj-C interop.
- **Phase 1** — the raw-dispatch path in `jetwhale-protocol`, then the Swift façade above (config
  builder, `JetWhalePlugin` protocol, `Codable` messenger) and the
  `SwiftPluginBridge`/`RawMessenger` Kotlin layer. Custom plugins fully usable. No dependency on
  Swift Export.
- **Phase 2** — the URLSession Network Inspector adapter.
- **Phase 3** — migrate the façade's interop from Obj-C to Swift Export as it matures, keeping the
  Swift API stable.

## Open questions

- The raw-dispatch API's exact shape: a fallback on `JetWhaleMessageHandlers`, or a separate raw
  plugin base the dispatcher recognizes.
- Whether official plugins beyond the first package's set can ship separately, given that
  Kotlin/Native frameworks do not compose (see the packaging section).
- Minimum Kotlin/Swift/Xcode versions to commit to.
