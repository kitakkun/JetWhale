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

Installed plugins live in `~/.jetwhale/plugins/`. There are three ways to install one:

- **Official Plugins** — one-click install for officially published plugins (e.g. the Network
  Inspector), no coordinates needed. The artifact version matching the running host is fetched
  from Maven Central (snapshot hosts fetch the matching snapshot build).
- **Install from Maven** — enter the plugin's `group:artifact:version` (append
  `@https://your.repo/url` for a repository other than Maven Central; pasting a Gradle dependency
  line or a Maven `<dependency>` block also works). The host downloads the plugin jar and the
  external dependencies it declares (stored in `~/.jetwhale/plugins/libs/`).
- **Add Plugin from File** — pick a locally built fat-jar, or drop one into `~/.jetwhale/plugins/`
  yourself (or run `./gradlew installPlugin` from a plugin project).

See [Developing Plugins](/guide/developing-plugins) for building your own.

### Plugin trust

Plugin jars are arbitrary code running inside the host process, so JetWhale only loads jars you
have explicitly approved. Approvals are recorded in `~/.jetwhale/trusted-plugins.json`, with each
jar pinned to the SHA-256 hash of its content at approval time. On startup:

- Jars whose current content still matches their pinned hash are loaded.
- Jars that were never approved, or whose content changed since approval, are **not** loaded and
  appear in the **Unverified Plugins** section of the settings screen for review.

Installing a plugin through the file picker counts as approval; jars dropped into the directory by
anything else must be approved manually. Revoking trust unloads the plugin immediately.

::: warning Threat model
This is an entry-side defense: it stops JetWhale from executing jars you never vouched for, and
detects jars swapped out after approval. It does **not** defend against malicious software already
running with your user privileges — such software can rewrite the trust registry (or JetWhale
itself) directly. Protecting against an attacker who already controls your user account is outside
the scope of this mechanism.
:::
