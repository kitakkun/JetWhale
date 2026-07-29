# Host Settings

The JetWhale host's behavior is configured from its **Settings** screen, opened from the gear icon in
the [drawer](/guide/host-window#the-rest-of-the-drawer).

Settings are organized into four **sections**, each holding one or more **pages**:

| Section | Pages |
|---------|-------|
| **General** | [Appearance](#appearance), [Application](#application) |
| **Connection** | [Debug Server](#debug-server), [SSL Certificate](#ssl-certificates), [ADB Support](#adb-support) |
| **AI Agents** | [MCP Server](#mcp-server), [Permissions](#mcp-permissions) |
| **Plugins** | [Installed Plugins](#plugins), [Add Plugins](#plugins), [Security](#plugin-trust) |

::: tip Window size and position
The host remembers its window size and position across launches automatically (when the window is
in normal floating state — maximized/fullscreen state is not persisted). There is nothing to
configure.
:::

## General

### Appearance

| Setting | Description |
|---------|-------------|
| **Language** | UI language of the host: **English** or **Japanese**. |
| **Theme** | Color scheme: `builtin:dynamic`, `builtin:light`, or `builtin:dark`. |

### Application

Everything about *this install* of the host.

**Maintenance**

- **Application Data Directory** — shows the host's app-data path (normally `~/.jetwhale/`) with a
  shortcut to open it in your file manager.
- **View Application Logs** — opens the built-in [log viewer](/guide/host-window#the-log-viewer).

**Updates**

- **Current Version** — the running host version.
- **Check for updates on startup (notify only)** — toggle the automatic check. Updates are never
  applied automatically.
- **Check for Updates** — check immediately. When one is found you can **Install and Relaunch** (on
  the platforms that support in-app updates) or **Open Download Page**.

## Connection

### Debug Server

The **Settings → Connection → Debug Server** page configures the WebSocket server debuggee apps
connect to.

| Setting | Default | Description |
|---------|---------|-------------|
| **Debug Server Port** | `5080` | The plain **ws** port. Must match the `port` in your app's `startJetWhale { connection { ... } }` block. |

Editing the port reveals an **Apply** button, which confirms before restarting the server —
restarting disconnects every session.

The status line above it shows the running ports, e.g. *Running on port 5080 (WSS: 5443)* when wss is
active, and offers **Retry** if the server failed to bind.

::: tip The wss port has no field of its own
The **secure WebSocket (wss)** port (default `5443`) and whether the wss connector is exposed at all
are not editable on this screen. They are set at launch with [`--wss-port`](#overriding-the-ports-at-startup),
or by an AI agent through `jetwhale.updateSettings` (`wssPort` / `wssEnabled`). In practice you only
need to generate a certificate under [SSL Certificate](#ssl-certificates) and point the agent's
`port` at 5443.
:::

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

### ADB support

The **Settings → Connection → ADB Support** page carries the Android port forwarding, plus where
the host found the tool it needs for it:

| Setting | Default | Description |
|---------|---------|-------------|
| **Automatically wire ADB port to host PC port** | on | Runs `adb reverse` for Android devices as they connect. Inactive on machines without `adb`. See [ADB Auto Port Mapping](/guide/adb-auto-port-mapping). |

Under **Health Check**, **ADB Executable Path** shows where JetWhale found `adb` (see
[How adb is found](/guide/adb-auto-port-mapping#how-adb-is-found)), or *ADB command not found*.

## AI Agents

### MCP Server

The **Settings → AI Agents → MCP Server** page configures the built-in
[MCP server](/guide/mcp-server) for AI agents.

| Setting | Default | Description |
|---------|---------|-------------|
| **MCP Server Port** | `7080` | Port the MCP server's SSE endpoint listens on, bound to localhost. |

Below it are copy-ready connection snippets — a `claude mcp add` command and a JSON config block,
both carrying the port the MCP server is *currently* running on — plus **Open setup guide**, which
links to the [MCP Server](/guide/mcp-server) page.

Changing the MCP server port here restarts the MCP server immediately (after a confirmation). An
agent changing it through `jetwhale.updateSettings` only persists the value — restarting would drop
the agent's own connection — so that change takes effect on the next host start.

### MCP Permissions

The **Settings → AI Agents → Permissions** page is a checkbox tree deciding what an AI agent may do,
from read-only observation through to restarting the debug server. See
[MCP Server → Permissions](/guide/mcp-server#permissions).

## Plugins

The **Installed Plugins** page lists what is loaded; **Add Plugins** is where new ones come from and
**Security** holds the trust settings below.

Installed plugins live in `~/.jetwhale/plugins/`. There are three ways to install one:

- **Official Plugins** — one-click install from the official catalog, no coordinates needed. The
  catalog currently holds the [Network Inspector](/guide/network-inspector) and the
  [Nav3 Navigator](/guide/nav3-navigator). The artifact version matching the running host is fetched
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

The trust registry can additionally be protected by an HMAC-SHA256 signature. Whether it is signed is
defined by one fact: **does a signing key exist in your OS credential store?** There is no on/off flag
kept on disk — the **Sign plugin trust registry** toggle in the plugin settings screen *creates* that
key (on) or *deletes* it (off). The key lives only in the credential store, never in the app data
directory. This is **off by default** (no key).

- **Off (default — no key):** JetWhale does not sign the registry, and on startup the only
  credential-store interaction is a **prompt-free check that no key exists** — you are never prompted.
  The registry is read and written unsigned. The SHA-256 content pinning above still detects a
  swapped-out jar, but `trusted-plugins.json` itself is not tamper-protected.
- **On (key present):** enabling it provisions a key and re-signs the current registry; from then on
  the registry is signed on every write and verified on every launch (which reads the key back). Once
  a key exists, a registry whose signature is missing or does not match is rejected wholesale and
  every plugin is treated as untrusted — so rewriting `trusted-plugins.json`, or stripping its
  signature, cannot forge an approval. If the credential store is unavailable (e.g. a headless Linux
  session with no keyring), JetWhale logs a warning and loads the registry unverified.

Where the key is kept — and whether you're prompted — depends on the platform:

- **macOS** — the login **Keychain**. Reading the key on each launch shows a Keychain access prompt;
  choose **Always Allow** to suppress it on later launches. A plain *Allow* re-prompts every launch,
  and a re-signed/updated app build can invalidate the grant and ask again.
- **Windows** — encrypted with **DPAPI** under your user account. Access is transparent — no prompt.
- **Linux** — the **Secret Service** (GNOME Keyring / KWallet). Whether a prompt appears depends on
  your keyring setup; a headless session with no keyring falls back to unsigned.

::: warning Threat model
Plugin trust is an entry-side defense: it stops JetWhale from executing jars you never vouched for,
and the SHA-256 pinning detects jars swapped out after approval regardless of this setting. With
registry signing **off** (the default), an attacker who can write to `~/.jetwhale` can forge an
approval by editing `trusted-plugins.json` directly. Turning signing **on** genuinely raises the bar
to **compromising the OS credential store**: a file-writing attacker cannot forge an accepted
registry (with a key present, any unsigned or re-signed file is rejected), and cannot even turn
signing back off, because deleting the key requires credential-store access — not just a file write.
What stays outside scope is an attacker who can already reach the credential store, or who runs code
as you and modifies JetWhale itself; protecting against full control of your user account is not a
goal of this mechanism.
:::

## Command-line options

### Overriding the ports at startup

Each port can also be chosen on the command line, which is handy when several hosts have to run side
by side (for example one per checkout of the app you are debugging) and would otherwise fight over
the same defaults:

| Option | Overrides |
|--------|-----------|
| `--server-port <port>` | **Debug Server Port** |
| `--wss-port <port>` | **wss port** |
| `--mcp-server-port <port>` | **MCP Server Port** |

An option that is not passed keeps using the saved setting. `--wss-port` only picks the port: wss is
still served only when it is enabled in the settings.

An override applies to that launch only and is never written back, but for as long as it is in force
it *is* the port the host reports — the settings screen shows it, so the screen and the running
server never disagree. Changing a port on that screen afterwards wins: the new value is saved and the
override for that port is retired for the rest of the session.

Pass them to the [runnable uber jar](/guide/getting-started):

```bash
java -jar jetwhale-host-<version>-<osArch>.jar --server-port 5081 --mcp-server-port 7081
```

When you launch the host from a plugin project with
[`runJetWhale`](/guide/developing-plugins) (or `runJetWhaleHot`), pass them with `--args`:

```bash
./gradlew :myPlugin:runJetWhale --args="--server-port 5081 --mcp-server-port 7081"
```

### Other options

| Option | Default | What it does |
|--------|---------|--------------|
| `--plugin-dir <path>` | — | Load plugins from an additional directory, on top of `~/.jetwhale/plugins/`. **Repeatable.** |
| `--log-level <level>` | `WARN` | Minimum level the host's own logging emits: `DEBUG`, `INFO`, `WARN` or `ERROR`. Raise it when diagnosing a plugin that will not load, then read the result in the [log viewer](/guide/host-window#the-log-viewer). |
| `--mcp-allow-all-permissions` | off | Allows every MCP tool for that process only — see [MCP Server → Lifting every permission for one launch](/guide/mcp-server#lifting-every-permission-for-one-launch). |

Ports are validated at parse time (they must be in `1..65535`), so a typo is reported immediately
rather than as a bind failure later. Unrecognized arguments are ignored.

::: tip Plugin developers use a sandbox, not `~/.jetwhale`
`runJetWhale` / `runJetWhaleHot` also set `-Djetwhale.appDataDir` and `-Djetwhale.devPluginsDir`, so
the whole app-data directory described on this page is redirected into a per-project sandbox. See
[Developing Plugins → Isolated sandbox environment](/guide/developing-plugins#isolated-sandbox-environment).
:::
