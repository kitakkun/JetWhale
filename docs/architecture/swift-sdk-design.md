# Native Swift SDK — API design

Status: **design/proposal** (not yet implemented). Companion to the Swift-Export feasibility study.

## Goal

Let a **pure Swift / SwiftUI / UIKit** app (no Kotlin, no Compose) embed the JetWhale agent
idiomatically — `import JetWhale`, start it, register custom plugins, exchange messages — without
the Swift developer ever touching Kotlin idioms.

## Why a separate Swift-facing API

The existing agent API is Kotlin-idiomatic and leans on exactly the features that do **not** bridge
to Swift today (whether via Obj-C interop or the still-Alpha Swift Export):

- receiver-lambda DSLs (`startJetWhale { connection { … } }`),
- reified generics (`request<R>()`, `JetWhaleRequest<R>`),
- `@Serializable` message types (no `Codable` bridging),
- sealed marker interfaces,
- Kotlin `abstract class` plugins (Swift cannot subclass exported Kotlin classes).

So we do **not** try to export the Kotlin authoring surface. Instead we add a thin **façade layer in
`commonMain`** whose public shape is export-clean, and design a Swift wrapper on top of it. The
Kotlin API for Kotlin consumers is untouched.

## The one design rule

> **Only strings and plain values cross the Kotlin↔Swift boundary.** All generic typing and
> serialization lives on the Swift side (`Codable`).

This is already possible: `JetWhaleMessenger` exposes a monomorphic boundary today —
`sendRaw(messageType: String, payload: String): Boolean` and
`suspend requestRaw(messageType, payload, timeout): String`
(`jetwhale-protocol/core/src/commonMain/kotlin/com/kitakkun/jetwhale/protocol/messaging/JetWhaleMessenger.kt:32,40`).
The typed `trySend`/`request` are just
reified extensions over these. The Swift SDK builds directly on the raw pair and does its own
`Codable` encode/decode, so generics and serialization never need to bridge.

## Distribution

Ship as a **Swift Package with a binary `.xcframework`** target
(`.binaryTarget(name:url:checksum:)`, zip on GitHub Releases). The repo already builds a static
XCFramework for the demo (`demo/shared/build.gradle.kts:61-67`) and `agent-runtime` targets
`iosArm64` / `iosSimulatorArm64` / `macosArm64`. Recommended: a **dynamic** framework for a
distributed SDK (avoids duplicate-symbol issues when linked into app extensions), automated with
Touchlab **KMMBridge** (build → upload → refresh checksum in `Package.swift`). Build the XCFramework
from the **Obj-C interop** path today; swap to Swift Export later without changing the Swift API.

## Swift-facing API (consumer view)

```swift
import JetWhale

// 1. Start — a value config, not a receiver-lambda DSL
JetWhale.start(host: "localhost", port: 5443) { config in
    config.appName = "My App (staging)"
    config.logging(.info)
    config.ssl(.trustServerCertificate)          // or .trustCertificate(pem: "…")
    config.register(NetworkPlugin())             // official plugins
    config.register(MyPlugin())                  // your own
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

    func configure(_ handlers: JetWhaleHandlers) {
        handlers.onEvent(ButtonClicked.self) { event in
            print("clicked \(event.count)")
        }
        handlers.onRequest(Ping.self) { _ in
            Pong(ok: true)                       // reply is the declared Reply type
        }
    }

    // Lifecycle — plain callbacks; async where the Kotlin side suspends
    func onActivate(_ messenger: JetWhaleMessenger) { self.messenger = messenger }
    func onPrepare(_ messenger: JetWhaleMessenger) async throws {
        let cfg: MockConfig = try await messenger.request(GetMockConfig())
        apply(cfg)
    }
    func onDisconnected() {}
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
- `JetWhalePlugin` — a Swift protocol (backed by an Obj-C protocol from Kotlin; Swift *can* conform
  to exported Kotlin interfaces even though it can't subclass Kotlin classes).
- `JetWhaleHandlers` — closure registry: `onEvent(_:_:)`, `onRequest(_:_:)`.
- `JetWhaleMessenger` — `trySend`, `sendOrQueue`, `sendOrFail`, and `async` `request`.
- `JetWhale.start(host:port:_:)` — trailing-closure builder over a `JetWhaleConfig` value type.

## Kotlin-side façade (what backs it)

Add to `commonMain` (new, export-clean; nothing generic/suspend-receiver in the public shape):

1. **`JetWhaleSwiftConfig`** — a plain class with settable properties (`appName`, `host`, `port`,
   `logLevel`, an `ssl` enum/holder) and `register(plugin)`. `JetWhale.start` (Swift) fills one and
   hands it to a non-suspend `startJetWhaleFromConfig(config)` that internally builds today's DSL.

2. **`SwiftPluginBridge`** (Kotlin `interface` → Obj-C protocol the Swift plugin conforms to):
   ```kotlin
   interface SwiftPluginBridge {
       val pluginId: String
       fun onActivate(messenger: RawMessenger)
       fun onPrepare(messenger: RawMessenger, done: () -> Unit)   // async → callback
       fun onDisconnected()
       fun onDeactivate()
       fun handleEvent(messageType: String, payloadJson: String)
       fun handleRequest(messageType: String, payloadJson: String): String  // returns reply json
   }
   ```
   A Kotlin `SwiftBackedAgentPlugin(bridge) : JetWhaleAgentPlugin` adapts it: its `configure`
   registers a single catch-all raw handler that dispatches `(messageType, json)` to
   `bridge.handleEvent/handleRequest`; its lifecycle hooks forward to the bridge. The Swift side
   implements `SwiftPluginBridge` inside a wrapper around the developer's `JetWhalePlugin`.

3. **`RawMessenger`** — a narrow export of the raw messenger: `trySendRaw(type, json): Bool`,
   `sendOrQueueRaw`, `sendOrFailRaw`, and (Kotlin 2.4 `suspend`→Swift `async`) `requestRaw(type,
   json): String`. The Swift `JetWhaleMessenger` wraps it and does `Codable` on both sides.

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

- `request` / `onPrepare` → Swift `async` (Kotlin 2.4 exports `suspend`→`async`); until that path is
  proven, back them with completion-handler overloads.
- Observable plugin state (host→agent `StateFlow`) → expose as an `AsyncStream`/`AsyncSequence`
  (Kotlin 2.4 `Flow`→`AsyncSequence`) or a `subscribe(_:)` callback. `StateFlow.value` bridging is
  undocumented in Swift Export, so provide an explicit accessor on the façade.
- Threading: `request`/handlers run on the runtime's coroutine scope; the façade must document/main-
  thread-hop where Swift callers expect it and honor Swift task cancellation.

## Network Inspector on Swift (Phase 2)

The Network Inspector core is transport-agnostic — `JetWhaleNetworkAgentPlugin` exposes
`recordRequest`/`recordResponse`/`recordFailure`/`findMock`/`newTransactionId`
(`jetwhale-plugins/network/agent/src/commonMain/kotlin/com/kitakkun/jetwhale/plugins/network/agent/JetWhaleNetworkAgentPlugin.kt:79-94`),
and its docstring invites new adapters.
A Swift app uses **URLSession**, which has no global interceptor, so ship a Swift-native adapter:

- capture via a `URLProtocol` subclass (or `URLSessionTaskDelegate` + `URLSessionTaskMetrics`),
- mock-serving by having the `URLProtocol` synthesize responses from `findMock`,
- feed everything into the existing capture API — no host-side changes.

SSE/streaming and background-session parity with the Ktor adapter are the hard parts.

## Phasing

- **Phase 0** — publish `agent-runtime` (+ SDK/protocol) as an XCFramework; prove a pure-Swift app
  can call a hand-written thin Kotlin façade over Obj-C interop.
- **Phase 1** — ship the Swift façade above (config builder, `JetWhalePlugin` protocol, `Codable`
  messenger) + the `SwiftPluginBridge`/`RawMessenger` Kotlin layer. Custom plugins fully usable.
  No dependency on Swift Export.
- **Phase 2** — the URLSession Network Inspector adapter.
- **Phase 3** — migrate the façade's interop from Obj-C to Swift Export as it matures, keeping the
  Swift API stable.

## Open questions

- Static vs dynamic XCFramework for the SDK (extension-linking, size).
- Exact discriminator scheme kotlinx.serialization uses on the host, and how to pin the Swift
  `messageType` to it without a shared Kotlin module.
- Whether to ship official plugins (Network Inspector) as separate Swift packages/targets.
- Minimum Kotlin/Swift/Xcode versions to commit to for the `suspend`→`async` export path.
