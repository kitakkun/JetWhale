# JetWhale

JetWhale is a next-generation, extensible debugging tool inspired
by [Flipper](https://github.com/facebook/flipper).

It is built with Kotlin and Jetpack Compose, making it especially familiar and approachable for
Kotlin / Android developers.
Thanks to its Kotlin-first design, JetWhale can be introduced with a minimal learning curve.

> [!NOTE]
> This project is under active development.
> We welcome feedback as we work toward a stable release.
> Please note that the Plugin SDK APIs are not yet finalized and may change in the future.

## Features

- 🐳 **Powerful Debugging Platform**
    - Provides a modern and rich debugging experience powered by **Kotlin** and **Jetpack Compose**
    - Supports debugging **multiple sessions simultaneously**
    - Debugging tools are implemented as **plugins**, which can be dynamically loaded at runtime as
      JAR files

- ⚙️ **Easy Integration and Customization**
    - DSL-based APIs allow you to quickly set up and configure JetWhale in your application
    - Customize the debugging experience by creating your own plugins using familiar **Kotlin**
      and **Jetpack Compose** paradigms

- 🛜 **Type-safe Communication with kotlinx.serialization**
    - Leverages **kotlinx.serialization** to enable type-safe communication between the debugger
      and debuggees

- ✅ **Multiplatform Support**
    - Supports **Android**, **Desktop(JVM)**, **iOS**(Simulator Only), and **Web** (Js, WasmJs)
      debuggees

- 🤖 **MCP Server Support** *(Experimental)*
    - JetWhale exposes a built-in **MCP (Model Context Protocol) HTTP+SSE server**, allowing AI
      agents (e.g. Claude) to interact with debuggee apps directly
    - Built-in tools include `screenshot`, `click`, `type`, `scroll`, `drag`, and
      `getAccessibilityTree`
    - Plugins can expose their own custom MCP tools by implementing `JetWhaleMcpCapablePlugin`

## Developing plugins

A host plugin is a fat-jar that JetWhale loads at runtime. Apply the `jetwhale-plugin` Gradle
convention to a plugin's host module to get the following tasks for free (see
`jetwhale-plugins/example/host` for a working example):

| Task            | What it does                                                                                   |
|-----------------|------------------------------------------------------------------------------------------------|
| `packagePlugin` | Builds the distributable plugin fat-jar (the artifact you drop into `~/.jetwhale/plugins/`).   |
| `installPlugin` | Copies the packaged fat-jar into `~/.jetwhale/plugins/`.                                        |
| `runJetWhale`   | Launches the JetWhale host with your plugin loaded from a private dev directory (`runIde`-like).|

### Live reload (dev mode)

`runJetWhale` starts the host with `-Djetwhale.devPluginsDir=<dir>` pointing at a dev directory
under the module's `build` folder. The host loads plugins from that directory **in addition to**
`~/.jetwhale/plugins/` and watches it: whenever the plugin jar is rebuilt, the host disposes the
plugin's running instances, drops its classloader, reloads the factory from a fresh classloader, and
refreshes the open plugin screen — no host restart needed.

`runJetWhale` is a long-running process (it blocks until you close the host), so do **not** add
`-t` to it — Gradle continuous mode only starts a new build once the current task graph finishes.
Instead, run the host in one terminal and continuous re-staging in another; the host watches the
dev directory and hot-reloads whenever the jar is re-staged:

```shell
# Terminal 1 — launch the host (stays running)
./gradlew :jetwhale-plugins:example:host:runJetWhale

# Terminal 2 — rebuild & re-stage the plugin jar on every source change
./gradlew :jetwhale-plugins:example:host:stageDevPlugin -t
```

When the `jetwhale.devPluginsDir` system property is absent (i.e. a normal production launch), dev
mode and hot reload are completely inert — behaviour is unchanged.
