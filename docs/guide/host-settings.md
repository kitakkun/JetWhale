# Host Settings

The JetWhale host's behavior is configured from its **Settings** screen.

## General

| Setting | Default | Description |
|---------|---------|-------------|
| **ADB auto port mapping** | off | Automatically runs `adb reverse` for Android devices as they connect. See [ADB Auto Port Mapping](/guide/adb-auto-port-mapping). |
| **Persist data** | off | Not yet implemented — the toggle exists but has no effect in the current release. |

## Server

The **Settings → Server** screen configures the debug WebSocket server and its TLS certificates.

| Setting | Default | Description |
|---------|---------|-------------|
| **Debug server port** | `5080` | The plain **ws** port debuggee apps connect to. Must match the `port` in your app's `startJetWhale { connection { ... } }` block. |
| **wss port** | `5443` | The **secure WebSocket (wss)** port, served alongside plain ws when a certificate is active. Point the agent's `port` at this when it connects over `ssl { }`. |
| **MCP server port** | `7080` | Port of the built-in [MCP server](/guide/mcp-server) for AI agents. |

The server status line shows the running ports, e.g. *Running on port 5080 (WSS: 5443)* when wss is
active.

### SSL certificates

To let agents connect over [wss](/guide/getting-started#secure-connections-wss), the host serves TLS
using a **locally-issued certificate**. Each entry is a self-contained local PKI: a root CA plus a
`localhost` server certificate signed by it. The host serves wss with the server certificate; the
agent trusts the CA.

From the **SSL Certificate** section you can:

- **Add Certificate** — generate a new CA + server certificate and mark it active.
- **Set Active** — switch which certificate the server uses. Multiple certificates can coexist so a
  certificate can be rotated without immediately invalidating apps still pinning the previous one.
- **Delete** — remove a certificate. If the active one is deleted, the first remaining certificate
  becomes active.
- **Show Details** — view the CA certificate in PEM form and **Copy to Clipboard**, to paste into an
  agent's `ssl { trustCertificate(pem = "...") }`.

::: warning Restart required
Certificate changes (generate, activate, delete) only take effect after the **debug server
restarts**. When the active certificate changes, the host prompts to restart the server now or
later; new wss connections use the new certificate once it has restarted.
:::

### LAN exposure

- **Plain ws** listens on **localhost only** — it is reachable from a device only via ADB reverse
  forwarding, so its traffic never leaves the machine.
- **wss** listens on **all interfaces**, so physical devices on the same network (e.g. an iPhone)
  can connect. The channel is encrypted and clients pin the local CA, so exposure is limited to the
  encrypted endpoint.
- This machine's current LAN IP addresses are embedded as Subject Alternative Names in the server
  certificate **at generation time**. If your machine's IP changes, **regenerate the certificate**
  so LAN clients still pass hostname verification.

## Plugins

Installed plugins live in `~/.jetwhale/plugins/`. There are three ways to install one:

- **Official Plugins** — one-click install for officially published plugins (e.g. the Network
  Inspector), no coordinates needed. The artifact version matching the running host is fetched
  from Maven Central, falling back to the matching snapshot build when the release is not
  published yet (snapshot hosts fetch their matching snapshot directly).
- **Install from Maven** — enter the plugin's `group:artifact:version` and pick a repository
  preset (Maven Central, Central Snapshots, Google, JitPack) or a custom URL. Pasting a plain
  coordinate line (optionally with `@https://your.repo/url`), a Gradle dependency line, or a Maven
  `<dependency>` block fills the fields automatically. The host downloads the plugin jar and the
  external dependencies it declares (stored in `~/.jetwhale/plugins/libs/`).
- **Add Plugin from File** — pick a locally built fat-jar, or drop one into `~/.jetwhale/plugins/`
  yourself (or run `./gradlew installPlugin` from a plugin project).

Jars that cannot be loaded (built for a different JetWhale version, missing dependencies, or not
valid plugin jars) are listed under **Incompatible Plugins**, with the concrete failure reason
shown per jar.

See [Developing Plugins](/guide/developing-plugins) for building your own.

### Plugin trust

Plugin jars are arbitrary code running inside the host process, so JetWhale only loads jars you
have explicitly approved. Approvals are recorded in `~/.jetwhale/trusted-plugins.json`, with each
jar pinned to the SHA-256 hash of its content at approval time. On startup:

- Jars whose current content still matches their pinned hash are loaded.
- Jars that were never approved, or whose content changed since approval, are **not** loaded and
  appear in the **Unverified Plugins** section of the settings screen for review.

Installing a plugin through the file picker, the Maven dialog, or the official catalog counts as
approval; jars dropped into the directory by anything else must be approved manually. Revoking
trust unloads the plugin immediately.

::: warning Threat model
This is an entry-side defense: it stops JetWhale from executing jars you never vouched for, and
detects jars swapped out after approval. It does **not** defend against malicious software already
running with your user privileges — such software can rewrite the trust registry (or JetWhale
itself) directly. Protecting against an attacker who already controls your user account is outside
the scope of this mechanism.
:::
