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
            host = "localhost"
            port = 5080 // the host's WebSocket server port
        }
        plugins {
            // register agent plugins here, e.g. the Network Inspector:
            // register(networkAgentPlugin)
        }
    }
}
```

Where to call it, per platform:

- **Android** — in `Application.onCreate()`
- **Desktop (JVM)** — first line of `main()`
- **Web (JS / WasmJS)** — first line of `main()`
- **iOS (Simulator)** — in your SwiftUI `App` init, e.g. `InitializeJetWhaleKt.initializeJetWhale()`

The [demo apps](https://github.com/kitakkun/JetWhale/tree/main/demo) show a complete multiplatform
setup with a shared `initializeJetWhale()` function.

An optional `logging { }` block controls agent-side logging
(`enabled = true`, `logLevel = LogLevel.WARN` by default).

## 4. Connect a device

- **Desktop / iOS Simulator / Web** debuggees reach the host directly on `localhost` — no extra
  setup.
- **Android** devices and emulators need `adb reverse` port forwarding. Enable
  [ADB auto port mapping](/guide/adb-auto-port-mapping) in the host settings and JetWhale wires it
  up automatically; or run `adb reverse tcp:5080 tcp:5080` yourself.

Launch your app — it appears as a new session in the host. JetWhale supports multiple simultaneous
sessions, so you can debug several apps or devices at once.

## Next steps

- [Network Inspector](/guide/network-inspector) — inspect and mock HTTP traffic
- [MCP Server](/guide/mcp-server) — let AI agents drive your app
- [Developing Plugins](/guide/developing-plugins) — build your own debugging tools
