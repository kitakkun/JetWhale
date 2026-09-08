<div align="center">
  <img src="assets/icon.png" alt="JetWhale icon" width="128" />
</div>

# JetWhale

[![Maven Central](https://img.shields.io/maven-central/v/com.kitakkun.jetwhale/jetwhale-agent-runtime)](https://central.sonatype.com/search?namespace=com.kitakkun.jetwhale)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License](https://img.shields.io/github/license/kitakkun/JetWhale)](LICENSE)

JetWhale is an extensible debugging tool inspired
by [Flipper](https://github.com/facebook/flipper).

It is built with Kotlin and Jetpack Compose, making it especially familiar and approachable for
Kotlin / Android developers.
Thanks to its Kotlin-first design, JetWhale can be introduced with a minimal learning curve.

## Features

- 🐳 **Multi-session debugging.** Several apps stay connected at once, grouped by the device they
  run on.
- 🔌 **Every tool is a plugin.** Add one to a running host; the shipped inspectors work the same way
  as yours.
- 🛜 **Type-safe messaging.** Plugins exchange Kotlin types rather than hand-parsed JSON.
- ✅ **Multiplatform.** Android, Desktop (JVM), iOS (simulator and physical devices) and Web
  (JS / WasmJS).
- ⚙️ **Zero Android setup.** The host wires `adb reverse` for you.
- 🤖 **[MCP server](https://kitakkun.github.io/JetWhale/guide/mcp-server)** *(experimental)*. An AI
  agent can drive the app — screenshot, click, type, scroll, drag, read the accessibility tree.
- 🔥 **Hot-reloadable plugin development.** Edit a plugin and the host reloads it, without
  restarting.

> [!NOTE]
> Under active development. Feedback is welcome as we work toward a stable release; the Plugin SDK
> APIs are not finalized and may still change.

📖 **Documentation: <https://kitakkun.github.io/JetWhale/>**

## Getting started

**1. Install the host** — download the installer for your OS from
the [releases page](https://github.com/kitakkun/JetWhale/releases) (`.dmg` for macOS, `.deb` for
Linux, `.msi` for Windows), and launch it.

**2. Add the runtime** to the app you want to debug:

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.kitakkun.jetwhale:jetwhale-agent-runtime:<version>")
}
```

**3. Start a session** as early as you can in your app's startup:

```kotlin
startJetWhale {
    connection {
        endpoints {
            ws("localhost", 5080)
        }
    }
    plugins {
        // each inspector below is its own artifact; register the ones you want
    }
}
```

That is the whole integration — the app shows up in the host as soon as it runs.

See **[Getting Started](https://kitakkun.github.io/JetWhale/guide/getting-started)** for physical iOS
devices, secure connections, and the rest.

## Official plugins

The debugging tools themselves are plugins. These ship with JetWhale — install one into the host
from its plugin catalog, add the matching artifact to your app, and register it in
`startJetWhale { }`:

- **[Network Inspector](https://kitakkun.github.io/JetWhale/guide/network-inspector)** — HTTP
  traffic with request and response bodies, plus mock rules that reshape responses without touching
  the server
- **[Compose Semantics Inspector](https://kitakkun.github.io/JetWhale/guide/compose-semantics-inspector)**
  — the Compose node tree of a running screen
- **[Nav3 Navigator](https://kitakkun.github.io/JetWhale/guide/nav3-navigator)** — the Navigation 3
  back stack, and pushing or popping entries from the host
- **[Android Device](https://kitakkun.github.io/JetWhale/guide/android-device)** — a connected
  device or emulator over adb: install, launch, tap, type, screenshot, logcat, all as MCP tools

## Developing plugins

Plugins are ordinary Gradle projects built against the published SDK. One command gives you a
hot-reload loop:

```bash
./gradlew :myPlugin:runJetWhaleHot
```

It downloads a real host for your OS, launches it with your plugin loaded, and reloads the plugin on
every source change without restarting.

See **[Developing plugins](https://kitakkun.github.io/JetWhale/guide/developing-plugins)** for the
full guide.
