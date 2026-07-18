# What is JetWhale?

JetWhale is a next-generation, extensible debugging tool inspired by
[Flipper](https://github.com/facebook/flipper).

It is built with Kotlin and Jetpack Compose, making it especially familiar and approachable for
Kotlin / Android developers. Thanks to its Kotlin-first design, JetWhale can be introduced with a
minimal learning curve.

::: warning Active development
This project is under active development. We welcome feedback as we work toward a stable release.
Please note that the Plugin SDK APIs are not yet finalized and may change in the future.
:::

## How it works

JetWhale consists of two sides connected over a WebSocket:

- **The host** — a desktop application (the debugger UI) that you run on your development machine.
  Debugging tools are implemented as **plugins**, loaded at runtime as JAR files.
- **The agent** — a small runtime you add to the app being debugged (the *debuggee*). It connects
  to the host and exchanges type-safe messages powered by kotlinx.serialization.

One host can debug **multiple sessions simultaneously** — for example an Android device and a
desktop app at the same time. Each session is labeled with the app's name and icon and grouped by
the device it runs on — resolved automatically, and customizable via the agent's
[`app { }` block](/guide/getting-started#session-metadata).

The connection is plain **ws** by default, and can be upgraded to **secure WebSocket (wss)** with a
locally-issued certificate — see [Secure connections](/guide/getting-started#secure-connections-wss).

### Session security indicator

Each session in the host shows a lock indicator for how its transport is secured:

- 🟢 **Green lock** — connected over TLS (**wss**); encrypted end to end.
- ⚪ **Neutral lock** — plain ws over a loopback peer (local or ADB-forwarded device). The traffic
  never leaves the machine, so it is effectively secure.
- **No lock** — plain ws to a non-loopback peer; the traffic is unencrypted on the network.

## Features

- 🐳 **Powerful debugging platform** — modern UI built with Kotlin and Jetpack Compose;
  multi-session debugging; runtime-loadable JAR plugins
- ⚙️ **Easy integration** — DSL-based setup in your app;
  [ADB auto port mapping](/guide/adb-auto-port-mapping) for zero-setup Android debugging
- 🛜 **Type-safe communication** — kotlinx.serialization between debugger and debuggee
- ✅ **Multiplatform debuggees** — Android, Desktop (JVM), iOS (Simulator & physical devices over wss), Web (JS, WasmJS)
- 🤖 **[MCP server](/guide/mcp-server)** *(experimental)* — AI agents can interact with your app
- 🔥 **[Hot-reloadable plugin development](/guide/developing-plugins)** — build plugins in your own
  repository against the published SDK

## Next steps

- [Getting Started](/guide/getting-started) — install the host and integrate the agent into your app
- [Network Inspector](/guide/network-inspector) — inspect and mock HTTP traffic
- [Developing Plugins](/guide/developing-plugins) — build your own debugging tools
