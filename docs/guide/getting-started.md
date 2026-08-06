# Getting Started

Debugging with JetWhale takes two pieces:

1. **The host** — the desktop debugger app you run on your machine.
2. **The agent runtime** — a small library you add to the app you want to debug.

## 1. Install the host

Download the installer for your OS from the
[GitHub releases page](https://github.com/kitakkun/JetWhale/releases):

| OS | Artifact |
|----|----------|
| macOS (Apple Silicon) | `jetwhale-debugger-<version>-macos-arm64.dmg` |
| Linux (x64) | `jetwhale-debugger-<version>-linux-x64.deb` |
| Windows (x64) | `jetwhale-debugger-<version>-windows-x64.msi` |

A runnable uber-jar (`jetwhale-host-<version>-<osArch>.jar`) is also attached to each release if you
prefer `java -jar`.

Launch the host. By default it listens for debuggee connections on **port 5080**.

## 2. Add the agent runtime to your app

All artifacts are published to Maven Central under the group `com.kitakkun.jetwhale`:

```kotlin
// the app being debugged — build.gradle.kts
dependencies {
    implementation("com.kitakkun.jetwhale:jetwhale-agent-runtime:<version>")
}
```

::: warning Kotlin version compatibility
JetWhale artifacts are built with a recent Kotlin release (currently **2.4.10**), and your app needs
**Kotlin 2.3 or newer** to use them. With an older Kotlin, the build fails with metadata-version
errors — upgrade your app's Kotlin plugin, or pick an older JetWhale release built with a matching
Kotlin.

If you cannot upgrade, `-Xskip-metadata-version-check` is an unofficial escape hatch:

```kotlin
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}
```

This only silences the metadata check — it does not guarantee compatibility. It is verified to
compile and run against the current release with Kotlin 2.0–2.2, but it is unsupported by JetBrains
and may break (especially around `inline` functions) with future releases. Prefer upgrading Kotlin.
:::

::: tip
Only add JetWhale to debug builds (e.g. `debugImplementation` on Android, or your own build-flavor
wiring) — it is a debugging tool and should not ship in release builds.
:::

## 3. Start JetWhale in your app

Call `startJetWhale { }` as early as possible in your app's startup:

```kotlin
import com.kitakkun.jetwhale.agent.runtime.startJetWhale

fun initializeJetWhale() {
    startJetWhale {
        connection {
            endpoint = fixed("localhost", 5080) // the host's WebSocket server port
        }
        plugins {
            // register agent plugins here, e.g. the Network Inspector:
            // register(networkAgentPlugin)
        }
    }
}
```

::: warning Always set `endpoint`
The agent's `connection { }` defaults to `fixed("localhost", 8080)`, while the host's debug server
listens on **5080**. The defaults deliberately do not match, so set `endpoint` explicitly to
whichever port the host reports in
[Settings → Connection → Debug Server](/guide/host-settings#debug-server).
:::

::: info `host` / `port` are deprecated
Earlier releases configured the address with separate `host` and `port` properties. They still work
and mean exactly `endpoint = fixed(host, port)`, but new code should assign `endpoint` — it is what
[host discovery](#zero-config-host-discovery-recommended-for-physical-devices) extends.
:::

Where to call it, per platform:

- **Android** — in `Application.onCreate()`
- **Desktop (JVM)** — first line of `main()`
- **Web (JS / WasmJS)** — first line of `main()`
- **iOS (Simulator & physical devices)** — in your SwiftUI `App` init, e.g. `InitializeJetWhaleKt.initializeJetWhale()`. A physical device connects over the local network via wss — see [Secure connections (wss)](#secure-connections-wss)

The [demo apps](https://github.com/kitakkun/JetWhale/tree/main/demo) show a complete multiplatform
setup with a shared `initializeJetWhale()` function.

### Logging

An optional `logging { }` block controls agent-side logging:

```kotlin
startJetWhale {
    connection { /* ... */ }
    logging {
        enabled = true                      // default
        logLevel = LogLevel.WARN            // default
        ktorLogLevel = KtorLogLevel.NONE    // default
    }
}
```

- **`logLevel`** is a minimum threshold over `LogLevel.VERBOSE`, `DEBUG`, `INFO`, `WARN`, `ERROR`,
  `ASSERT`. Nothing is logged at `ASSERT`, so setting it silences the agent entirely.
- **`ktorLogLevel`** gates the underlying Ktor client's own HTTP logging: `NONE` (default), `HEADERS`,
  `BODY`, `ALL`. Raise it only when diagnosing the connection itself — it is noisy.

There is no custom-logger hook, and the configuration is process-global: with several concurrent
sessions, the last `startJetWhale` call wins.

### Stopping a session

`startJetWhale` returns a **`JetWhaleSession`** handle:

```kotlin
val session = startJetWhale { /* ... */ }
// later
session.stop()
```

`stop()` tears the reconnect loop down and drops the plugins. It is **terminal** — a stopped session
cannot be revived, and repeated calls are ignored. To connect again, call `startJetWhale` a second
time with **fresh plugin instances** (a plugin is bound to the session it was registered with).
Teardown is asynchronous: `stop()` returns as soon as it is scheduled, and the host observes the
disconnect shortly after. Holding the handle is optional — most apps start one session for the
process lifetime and never stop it.

### Reconnecting

The agent retries **forever**. After a failed connection or negotiation it drops the plugins'
peers, waits, and tries again with a linear backoff that grows by one second per attempt and is
capped at five (1s, 2s, 3s, 4s, 5s, 5s, …); the counter resets on the first success. None of this is
configurable, and there is no connection-state API to poll — start the host before the app, or just
leave the app running until the host comes up.

A disconnect is **not** a deactivation: registered plugins stay activated across it, so an agent
plugin that buffers events keeps buffering for the next connection.

### Session metadata

The agent reports app/device metadata to the host during connection, and the host uses it to label
and group sessions (for example, all sessions from the same physical device). Everything is
resolved automatically on a best-effort basis — the app name on Android/iOS/macOS, a stable device
identifier and the device name per platform — so usually you configure nothing.

To override any of it (or supply values on platforms where auto-resolution is unavailable), add an
`app { }` block; explicit values always win over the auto-resolved defaults:

```kotlin
startJetWhale {
    connection { /* ... */ }
    app {
        appName = "My App (staging)"
        deviceName = "CI emulator"
        // deviceId = "..."            // stable id used to group sessions per device
        // appIconPng = iconPngBytes   // PNG, at most 64x64 px; dropped if base64 exceeds 32KB
    }
    plugins { /* ... */ }
}
```

How much is resolved for you differs sharply by platform — Android and iOS fill in everything,
desktop and web mostly do not:

| Platform | `appName` | `deviceId` | `deviceName` |
|----------|-----------|------------|--------------|
| **Android** | the application label | `Settings.Secure.ANDROID_ID` | `Build.MODEL` |
| **iOS** | `CFBundleDisplayName` / `CFBundleName` | `identifierForVendor` | the device name |
| **macOS (native)** | `CFBundleDisplayName` / `CFBundleName` | — | the host's localized name |
| **Desktop (JVM)** | — | — | the `os.name` system property |
| **Linux / Windows (native)** | — | — | the machine's host name |
| **Web (JS / WasmJS)** | — | — | `"Web Browser"` |

A dash means nothing is resolved and the field stays empty unless you set it in `app { }`. On
desktop and web in particular, setting `appName` is what makes a session readable in the host's
[device / app selector](/guide/host-window#choosing-a-session).

An `appIconPng` whose base64 form exceeds the 32KB cap is dropped with a warning — which is visible,
since `WARN` is the default log level.

## Zero-config host discovery (recommended for physical devices)

A physical iPhone or Android device on the same Wi-Fi cannot reach the host over `localhost`. Rather
than hardcoding the host machine's LAN IP (which changes between machines and networks), assign a
`discovered(...)` endpoint and let the agent find the host over mDNS/Bonjour:

```kotlin
startJetWhale {
    connection {
        // Browse the LAN for the host advertised as `_jetwhale._tcp` and connect to it. The fallback
        // applies when nothing is discovered in time (or the platform lacks mDNS), which keeps
        // emulators/simulators and ADB-forwarded devices working over localhost.
        endpoint = discovered(fallback = fixed("localhost", 5443))

        ssl { trustServerCertificate() }
    }
    plugins { /* ... */ }
}
```

The fallback is a required argument rather than a separate property, so a discovered endpoint can
never be left without one.

While its debug server runs, the host advertises a `_jetwhale._tcp.local.` service whose TXT records
carry the `wsPort` and `wssPort` (the latter only when wss is enabled), the host machine's `hostName`,
and a protocol marker `v=1`. Discovery therefore resolves the **port** as well as the address, and the
fallback's port applies only when discovery finds nothing.

The scheme is not negotiable: the agent uses wss exactly when an `ssl { }` block is configured. So a
host counts as a candidate only if it advertises the port for that scheme — an ssl-configured agent
**skips** a host whose wss is disabled, rather than dialling `wss://` at its plain-ws port. If every
matching host is skipped that way, the log says so by name, since the host being there but serving
the wrong scheme looks nothing like the host being absent.

### Narrowing discovery

When several JetWhale hosts run on the same network, first-match is ambiguous (the agent logs a
warning listing all discovered hosts). Narrow the selection with a filter block:

```kotlin
connection {
    endpoint = discovered(fallback = fixed("localhost", 5443)) {
        // Accept only a host advertising this machine hostname (exact, case-insensitive).
        allowHostName("my-macbook")

        // ...and/or only hosts resolving to specific IPs, e.g. to pin discovery to your build
        // machine — which may answer on both Wi-Fi and Ethernet.
        allowAddress("192.168.3.26")
        allowAddress("192.168.3.27")
    }
    ssl { trustServerCertificate() }
}
```

- **`allowHostName(name)`** — matches the advertised hostname exactly, compared case-insensitively.
  The compared value is the host machine's hostname (from the `hostName` TXT record, falling back to
  the mDNS instance name).
- **`allowAddress(ip)`** — matches a resolved IP. (On iOS/macOS the agent connects by the resolved
  `.local.` hostname, so prefer `allowHostName` there.)

Both are **repeatable allowlists**: calling one twice widens it rather than replacing the earlier
value. An empty allowlist means "no restriction on this", so a host must match every allowlist that
has entries — name **and** address when both are set, either entry within each.

Discovery is best-effort: if no matching host is found within a few seconds — or on a platform
without mDNS support (**JS/Wasm, Linux, Windows**) — the agent logs a warning and falls back to the
endpoint passed as `fallback`.

Falling back is **per attempt, not permanent**. The agent browses again before every connection
attempt, so [the usual reconnect promise](#reconnecting) still holds with discovery enabled: start the
app first and the host later, and the next browse picks it up. The same applies when a host restarts
on a different port. Warnings are not repeated while the outcome stays the same, so an unreachable
host does not flood the log.

| Platform | Discovery backend |
|----------|-------------------|
| **JVM (Desktop)** | jmDNS |
| **Android** | `NsdManager` (`android.net.nsd`) |
| **iOS / macOS** | `NSNetServiceBrowser` (Network.framework / Bonjour) |
| **JS / Wasm / Linux / Windows** | Not supported — falls back to the configured host |

On **iOS**, browsing for the service also requires listing it under `NSBonjourServices` in
`Info.plist` (see [iOS Local Network permission](#ios-local-network-permission)).

## Secure connections (wss)

By default the agent connects over plain **ws** (port **5080**). The host can additionally serve
**secure WebSocket (wss)** on port **5443**, backed by a locally-issued CA — see
[Host Settings → SSL certificates](/guide/host-settings#ssl-certificates) for generating and
activating a certificate.

To make the agent connect over wss, add an `ssl { }` block to `connection { }`. As soon as at least
one trusted certificate is configured, the connection switches from ws to wss:

```kotlin
startJetWhale {
    connection {
        endpoint = fixed("localhost", 5443) // the host's wss port

        ssl {
            // Option A: fetch and pin the host's active CA automatically (trust-on-first-use).
            trustServerCertificate()

            // Option B: pin a CA you exported from the host's SSL Certificate settings.
            // trustCertificate(pem = "-----BEGIN CERTIFICATE-----\n...")
        }
    }
    plugins { /* ... */ }
}
```

### Which one to use

- **`trustServerCertificate()`** — the agent downloads the host's active CA from `/jetwhale/ca` at
  connect time and pins the wss connection to it, so no PEM is hardcoded in the app. It probes the
  configured `port` in two topologies, in order:
    1. `http://<host>:<port>/jetwhale/ca` — the plain channel, used when `port` is the host's
       plain-ws port (localhost / ADB port forwarding).
    2. `https://<host>:<port>/jetwhale/ca` with certificate verification disabled — used when the
       plain fetch is unreachable, e.g. a LAN device (iPhone) connecting to the host's TLS server on
       the wss port while the host's plain server is bound to loopback.

  Both are a *trust-on-first-use* exchange: the fetch itself is not authenticated, and the disabled
  verification in step 2 is security-equivalent to the plain fetch in step 1 (the fetched CA still
  pins the subsequent wss session). Over ADB port forwarding (the usual case) the download never
  leaves the machine, so it is as trustworthy as the ADB link. If the CA cannot be fetched over
  either channel, the connection falls back to plain ws.
- **`trustCertificate(pem)`** — pins a CA PEM you exported yourself from the host's
  [SSL Certificate](/guide/host-settings#ssl-certificates) settings (**Show Details → Copy to
  Clipboard**). Prefer this on an untrusted LAN, where strict pinning
  matters.

### Per-platform pinning support

Certificate pinning is implemented per platform; behaviour differs where the platform's networking
stack constrains it:

| Platform | Behaviour |
|----------|-----------|
| **JVM / Android** | Full pinning via a custom `X509TrustManager` built from the configured PEMs. Invalid PEMs log a warning and fall back to system trust. |
| **iOS / macOS** | Full pinning via Security.framework anchor certificates (`SecTrustSetAnchorCertificates`), so the local CA is trusted without installing it in the device trust store. A physical iPhone reaches the host over the LAN and fetches the CA over the wss port; add the [Local Network permission](#ios-local-network-permission) so iOS allows it. |
| **Linux** | Pinning via curl's `CURLOPT_CAINFO`: the PEMs are written to a private per-process CA bundle file under the temp dir and pinned against it. |
| **Windows** | WinHttp validates only against the Windows certificate store and cannot pin a custom CA in code. Install the exported CA into the store manually, e.g. `certutil -user -addstore Root jetwhale-ca.pem`. |
| **Web (JS / WasmJS)** | The browser manages TLS; custom CA configuration is not supported and is ignored with a warning. |

### iOS Local Network permission

A physical iPhone connects to the host over the local network rather than `localhost`, and both the
wss connection and the trust-on-first-use CA fetch go over the LAN. iOS gates local-network access
behind a user permission, so add a usage-description string to the app's `Info.plist`:

```xml
<key>NSLocalNetworkUsageDescription</key>
<string>JetWhale connects to the debugger host running on your local network.</string>
<!-- Required when using discovered(): iOS blocks the Bonjour browse without it. -->
<key>NSBonjourServices</key>
<array>
    <string>_jetwhale._tcp</string>
</array>
```

iOS prompts the user to allow local-network access on the first connection. `NSBonjourServices` is
required whenever you use [`discovered()`](#zero-config-host-discovery-recommended-for-physical-devices):
iOS silently blocks browsing for a service type that is not declared. If you dial the host by an
explicit address instead of discovering it, `NSBonjourServices` can be omitted. Because the CA fetch
falls back to `https` over the wss port, no App Transport Security exception for plain HTTP is needed.

## 4. Connect a device

- **Desktop / iOS Simulator / Web** debuggees reach the host directly on `localhost` — no extra
  setup.
- **Android** devices and emulators need `adb reverse` port forwarding.
  [ADB auto port mapping](/guide/adb-auto-port-mapping) is on by default, so JetWhale wires it up
  automatically; turn it off in the host settings to run `adb reverse tcp:5080 tcp:5080` yourself.

Launch your app — it appears as a new session in the host. JetWhale supports multiple simultaneous
sessions, so you can debug several apps or devices at once.

## Published artifacts

Everything is published to Maven Central under the group **`com.kitakkun.jetwhale`**, all at the same
version as the host release they belong to.

| Artifact | Where it goes |
|----------|---------------|
| `jetwhale-agent-runtime` | The app being debugged. Brings `jetwhale-agent-sdk` and `jetwhale-protocol-core` with it, so you rarely need to name those. |
| `jetwhale-agent-sdk` | Only when a module writes agent plugins without depending on the runtime. |
| `jetwhale-protocol-core` | The shared module of a plugin pair, for `JetWhaleEvent` / `JetWhaleRequest`. |
| `jetwhale-annotations` | `@McpDescription`. Reaches both SDKs transitively; rarely named directly. |
| `jetwhale-host-sdk` | A host plugin module, as `compileOnly` — see [Developing Plugins](/guide/developing-plugins). |
| `jetwhale-gradle-plugin` | Applied as the `com.kitakkun.jetwhale.host` Gradle plugin id. |
| `jetwhale-qa-agent` | Run, not depended on — see [QA Agent](/guide/qa-agent). |
| `jetwhale-network-inspector`, `-agent`, `-agent-ktor`, `-agent-okhttp`, `-protocol` | [Network Inspector](/guide/network-inspector). |
| `jetwhale-nav3-navigator`, `jetwhale-nav3-agent`, `jetwhale-nav3-protocol` | [Nav3 Navigator](/guide/nav3-navigator). |
| `jetwhale-compose-semantics-inspector`, `-agent`, `-protocol` | [Compose Semantics Inspector](/guide/compose-semantics-inspector). |

The `-navigator` / `-inspector` artifacts (no suffix) are the **host** plugin jars — you install
those into the host rather than into your app; see [Host Settings → Plugins](/guide/host-settings#plugins).

::: warning Published Kotlin Multiplatform targets
The multiplatform artifacts ship `jvm`, `android`, `js(IR)`, `wasmJs`, `iosArm64`,
`iosSimulatorArm64`, `macosArm64`, `mingwX64`, `linuxX64` and `linuxArm64`. There is **no `iosX64`
and no `macosX64`**, so an Intel Mac (and the Intel iOS simulator) cannot resolve them; and there are
no watchOS/tvOS targets.
:::

## Next steps

- [The Host Window](/guide/host-window) — find your way around the debugger UI
- [Network Inspector](/guide/network-inspector) — inspect and mock HTTP traffic
- [Compose Semantics Inspector](/guide/compose-semantics-inspector) — browse your app's Compose node tree and
  drive it by node
- [MCP Server](/guide/mcp-server) — let AI agents drive your app
- [Developing Plugins](/guide/developing-plugins) — build your own debugging tools
