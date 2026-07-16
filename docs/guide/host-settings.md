# Host Settings

The JetWhale host's behavior is configured from its **Settings** screen.

## General

| Setting | Default | Description |
|---------|---------|-------------|
| **Server port** | `5080` | The WebSocket port debuggee apps connect to. Must match the `port` in your app's `startJetWhale { connection { ... } }` block. |
| **ADB auto port mapping** | off | Automatically runs `adb reverse` for Android devices as they connect. See [ADB Auto Port Mapping](/guide/adb-auto-port-mapping). |
| **Persist data** | off | Not yet implemented — the toggle exists but has no effect in the current release. |

## Server

| Setting | Default | Description |
|---------|---------|-------------|
| **MCP server port** | `7080` | Port of the built-in [MCP server](/guide/mcp-server) for AI agents. |

## Plugins

Installed plugins live in `~/.jetwhale/plugins/` as fat-jars. Drop a plugin jar there (or run
`./gradlew installPlugin` from a plugin project) and the host picks it up. See
[Developing Plugins](/guide/developing-plugins) for building your own.
