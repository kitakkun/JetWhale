# Configuring the Agent

`startJetWhale { }` configures the agent runtime inside your app. Its `connection { }` block is
covered in [Connecting Devices](/guide/connecting); this page covers the rest.

## Registering plugins

`plugins { register(...) }` hands the agent the app-side half of each plugin; each plugin's guide
shows its own registration. A plugin instance is bound to the session it was registered with, so a
new session needs fresh instances.

## Session metadata

The agent reports app and device metadata when it connects. The host uses it to label sessions and
to group them by device in its [app picker](/guide/host-window#choosing-an-app). Most of it is
resolved automatically, on a best-effort basis, so usually you configure nothing.

An `app { }` block overrides any of it, or supplies it where nothing is resolved; explicit values
always win:

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

Android and iOS fill in everything; desktop and web mostly do not:

| Platform | `appName` | `deviceId` | `deviceName` | `appIconPng` |
|----------|-----------|------------|--------------|--------------|
| **Android** | the application label | `Settings.Secure.ANDROID_ID` | `Build.MODEL` | the launcher icon, rasterized to 64x64 |
| **iOS** | `CFBundleDisplayName` / `CFBundleName` | `identifierForVendor` | the device name | the bundle's icon, rasterized to 64x64 |
| **macOS (native)** | `CFBundleDisplayName` / `CFBundleName` | — | the host's localized name | the bundle's icon, rasterized to 64x64 |
| **Desktop (JVM)** | — | — | the `os.name` system property | — |
| **Linux / Windows (native)** | — | — | the machine's host name | — |
| **Web (JS / WasmJS)** | — | — | `"Web Browser"` | — |

A dash means nothing is resolved and the field stays empty unless you set it. On desktop and web,
setting `appName` is what makes a session readable in the picker. An `appIconPng` whose base64 form
exceeds 32 KB is dropped with a warning, which shows at the default log level.

## Logging

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
- **`ktorLogLevel`** gates the Ktor client's own HTTP logging: `NONE` (default), `HEADERS`, `BODY`,
  `ALL`. Raise it only when diagnosing the connection itself; it is noisy.

There is no custom-logger hook, and the configuration is process-global: with several concurrent
sessions, the last `startJetWhale` call wins.

## Session lifecycle

### Stopping a session

`startJetWhale` returns a **`JetWhaleSession`** handle:

```kotlin
val session = startJetWhale { /* ... */ }
// later
session.stop()
```

`stop()` tears the reconnect loop down and drops the plugins. It is **terminal**: a stopped session
cannot be revived, and repeated calls are ignored. To connect again, call `startJetWhale` again with
**fresh plugin instances**. Teardown is asynchronous, so `stop()` returns as soon as it is scheduled
and the host sees the disconnect shortly after. Most apps start one session for the process lifetime
and never hold the handle.

### Reconnecting

The agent retries **forever**. The unit it retries is a **round**: every candidate in
`endpoints { }`, in order. A candidate that refuses costs nothing but a move to the next one; a
candidate that swallows packets rather than refusing is abandoned after 30 seconds.

Only a round in which nothing accepted waits before the next one, with a linear backoff that grows by
one second per round, capped at five (1 s, 2 s, 3 s, 4 s, 5 s, 5 s, …). A round that served a session
costs no delay. Either way the plugins' peers are dropped and the next round starts from the top of
the list.

The backoff resets on a connection that **held**: a session that ends within two seconds of opening
counts as a failure, so a host that accepts the upgrade and drops it at once cannot make the agent
spin. None of this is configurable, and there is no connection-state API to poll; start the host
before the app, or leave the app running until the host comes up.

A disconnect is **not** a deactivation: registered plugins stay activated across it, so an agent
plugin that buffers events keeps buffering for the next connection.
