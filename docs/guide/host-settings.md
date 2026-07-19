# Host Settings

The JetWhale host's behavior is configured from its **Settings** screen.

::: tip Window size and position
The host remembers its window size and position across launches automatically (when the window is
in normal floating state — maximized/fullscreen state is not persisted). There is nothing to
configure.
:::

## General

The **Settings → General** screen groups the host-wide options:

### Appearance

| Setting | Description |
|---------|-------------|
| **Language** | UI language of the host: **English** or **Japanese**. |
| **Theme** | Color scheme: `builtin:dynamic`, `builtin:light`, or `builtin:dark`. |

### ADB support

| Setting | Default | Description |
|---------|---------|-------------|
| **ADB auto port mapping** | off | Automatically runs `adb reverse` for Android devices as they connect. See [ADB Auto Port Mapping](/guide/adb-auto-port-mapping). |

### Maintenance

- **Application data directory** — shows the host's app-data path (normally `~/.jetwhale/`) with a
  shortcut to open it in your file manager.
- **View application logs** — opens the built-in log viewer.

### Updates

- **Current version** — the running host version.
- **Check for updates on startup** — toggle the automatic update check.
- **Check for updates** — check immediately; when an update is found you can install it or open the
  download page.

### Health check

- **adb executable path** — shows where JetWhale found `adb` (see
  [How adb is found](/guide/adb-auto-port-mapping#how-adb-is-found)), or that it is unavailable.

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

TLS material is stored under `~/.jetwhale/ssl` with owner-only permissions (the keystore holds the
CA private key), and the generated CA carries name constraints limiting it to local/private
addresses (`localhost`, loopback, and the RFC 1918 / link-local ranges). If you install the CA into
an OS trust store — for example on the Windows WinHttp path — prefer the **current-user** store over
the machine-wide store.

::: tip Certificate changes apply immediately
Certificate changes (generate, activate, delete) take effect at once: the host hot-swaps only the
**wss** listener onto the new certificate while the plain **ws** listener keeps running untouched.
Connected wss clients drop and reconnect against the new certificate; plain-ws sessions are
unaffected.
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

#### Registry signing (opt-in)

The trust registry can additionally be protected by an HMAC-SHA256 signature whose key lives in the
OS credential store (macOS Keychain, Windows Credential Manager, or Linux Secret Service) — never in
the app data directory. This is **off by default**: the **Sign plugin trust registry** toggle in the
plugin settings screen turns it on.

- **Off (default):** the credential store is **never accessed**. The registry is read and written
  unsigned, so JetWhale never prompts you for Keychain access on startup. The SHA-256 content pinning
  above still applies, so a swapped-out jar is still detected — but the `trusted-plugins.json` file
  itself is not tamper-protected.
- **On:** enabling it provisions a key and re-signs the current registry (a first credential-store
  prompt). From then on the registry is signed on every write and **verified on every launch, which
  reads the key back** — so the OS asks for credential-store access at each startup, not just once.
  On macOS choose **Always Allow** at the Keychain prompt to suppress it on later launches (a plain
  *Allow* re-prompts every launch, and a re-signed/updated app build can invalidate the grant and
  ask again). A registry whose signature does not verify is rejected wholesale and every plugin is
  treated as untrusted, so rewriting `trusted-plugins.json` alone cannot forge an approval. If the
  credential store is unavailable (e.g. a headless Linux session), JetWhale logs a warning and falls
  back to loading the registry without signature verification.

::: warning Threat model
Plugin trust is an entry-side defense: it stops JetWhale from executing jars you never vouched for,
and the SHA-256 pinning detects jars swapped out after approval regardless of this setting. With
registry signing **off** (the default), an attacker who can write to `~/.jetwhale` can forge an
approval by editing `trusted-plugins.json` directly. Turning signing **on** raises the bar from
"write one file" to "also compromise the OS credential store" — but software already running with
your user privileges can still potentially do that, or modify JetWhale itself. Protecting against an
attacker who fully controls your user account is outside the scope of this mechanism.
:::
