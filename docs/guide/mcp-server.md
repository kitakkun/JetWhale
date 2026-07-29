# MCP Server <Badge type="warning" text="experimental" />

The JetWhale host embeds an **MCP (Model Context Protocol) server**, so AI agents such as Claude
can inspect and drive your debugging sessions. The built-in tools come in two families:

- **Plugin UI tools** operate on a **plugin's UI inside the host** — capture it, click, type,
  scroll, and read its semantics tree — so an agent can use any plugin the way you do (and, through
  a plugin's own capabilities, reach the debuggee app itself).
- **Host tools** operate on the **debug tool as a whole** — read its status and logs, install and
  enable plugins, change settings, and switch the window to another screen — so an agent can set
  JetWhale up rather than only using what you have already set up by hand.

## Connecting an AI agent

The server speaks MCP over **SSE**, listening on port **7080** by default (configurable in
**Settings**):

- `GET http://localhost:7080/sse` — the SSE stream
- `POST http://localhost:7080/message?sessionId=...` — client-to-server messages

For example, to register it with Claude Code:

```shell
claude mcp add --transport sse jetwhale http://localhost:7080/sse
```

The host's **Settings → AI Agents → MCP Server** section shows this command — and an equivalent JSON
config block for other MCP clients — already filled in with the port the server is actually running
on, ready to copy.

## Plugin UI tools

| Tool | What it does |
|------|--------------|
| `jetwhale.listSessions` | Lists connected debug sessions; other tools take a `sessionId` from here |
| `jetwhale.listPlugins` | Lists the plugins available in a session |
| `jetwhale.screenshot` | Captures the current rendered frame of a plugin's Compose UI as a PNG |
| `jetwhale.click` | Dispatches a mouse click at pixel coordinates in a plugin's UI |
| `jetwhale.type` | Types text or a special key into a plugin's UI |
| `jetwhale.scroll` | Dispatches a scroll event in a plugin's UI |
| `jetwhale.drag` | Simulates a drag gesture in a plugin's UI |
| `jetwhale.getAccessibilityTree` | Returns the Compose semantics (accessibility) tree of a plugin's UI |

Because a single host can debug multiple apps at once — each with several plugins — every tool that
targets a plugin UI takes required `sessionId` **and** `pluginId` parameters: call
`jetwhale.listSessions` first, then `jetwhale.listPlugins` to pick the plugin.

## Host tools

These target the debug tool itself rather than one plugin instance, so none of them takes the
required `sessionId` + `pluginId` pair the plugin UI tools route on. Some do take a `pluginId` or an
optional `sessionId` as ordinary arguments — which plugin to enable, which session to open — but the
tool is not scoped to them.

`jetwhale.getStatus` is the recommended first call: one parameter-free request tells an agent the
host version, both servers' endpoints, how many sessions and plugins are live, the current settings,
and what the window is showing.

| Tool | What it does |
|------|--------------|
| `jetwhale.getStatus` | One snapshot of the host: version, servers, session and plugin counts, settings, current screen |
| `jetwhale.getLogs` | Reads the host's own captured stdout/stderr, filterable by level and substring |
| `jetwhale.clearLogs` | Discards every captured host log entry |
| `jetwhale.listInstalledPlugins` | Lists installed plugins and their enabled state, official plugins still available, and any failed or untrusted jar |
| `jetwhale.setPluginEnabled` | Enables or disables an installed plugin, like the drawer toggle |
| `jetwhale.installOfficialPlugin` | Installs a plugin from the official catalog (see Permissions) |
| `jetwhale.updateSettings` | Changes host settings; only the arguments you supply are touched |
| `jetwhale.restartDebugServer` | Restarts the debug WebSocket server |
| `jetwhale.navigate` | Switches the main window to another screen, selecting the session and plugin as it goes |

`jetwhale.getLogs` reads the **host's** log, not the debuggee app's — use it to diagnose JetWhale
itself, for example a plugin jar that failed to load.

::: warning Destructive host tools
`jetwhale.restartDebugServer` — and `jetwhale.updateSettings` when it changes a ws/wss setting —
stops the debug server, which disconnects **every** session. Each `sessionId` an agent is holding
becomes invalid and each app has to reconnect. Pass `restartDebugServer: false` to `updateSettings`
to persist a change and apply it later instead.

Changing `mcpServerPort` never restarts the MCP server, because that would drop the agent's own
connection. The new port takes effect the next time the host starts.
:::

## Permissions

What an agent may do is controlled in **Settings → AI Agents → Permissions**, as a tree of nested
checkboxes.

| Group | Tools | Default |
|-------|-------|---------|
| **Observe** | `getStatus`, `getLogs`, `clearLogs`, `listInstalledPlugins` | on |
| **Navigate** | `navigate` | on |
| **Manage plugins** | `setPluginEnabled`, `installOfficialPlugin` | **off** |
| **Settings & servers** | `updateSettings`, `restartDebugServer` | **off** |

Each installed plugin gets a subtree of its own:

| | Covers | Default |
|---|---|---|
| **UI → Inspect** | `screenshot`, `getAccessibilityTree`, for that plugin | on |
| **UI → Interact** | `click`, `type`, `scroll`, `drag`, for that plugin | on |
| **Own tools** | one checkbox per MCP tool the plugin contributes | on |

Reading and driving are split because they are different risks: letting an agent look at a plugin's
screen is not the same as letting it press the buttons on it. Everything defaults to on — you
installed and enabled the plugin deliberately — and any leaf can be revoked on its own.

A plugin's own tools are only listed once the plugin has a live instance, since that is when it
publishes its commands; with nothing connected the subtree says so. Denials are keyed by tool name,
so they survive a disconnect and apply again the moment the tool comes back.

`jetwhale.listSessions` and `jetwhale.listPlugins` are never gated. They are the discovery calls
every other tool's arguments come from, so denying them would only leave an agent unable to name
what it is asking about.

The two defaults that are off both do something you cannot undo by unticking a box afterwards:
installing a plugin runs new code inside JetWhale, and restarting the debug server disconnects every
session. Groups are stored as what you allowed, so a group added by a future release starts off
rather than inheriting a yes you never gave.

**A permission bites in two places.** A denied group's tools are not listed at all on a new
connection, and every call is checked again as it arrives — so revoking something mid-session stops
the agent that is already connected, without waiting for it to reconnect. Re-allowing works the
other way round: the tool reappears on the agent's next connection, because a tool list is fixed
when the connection opens.

A refused call says which group or plugin blocked it and names the settings screen, so an agent can
tell you what to turn on rather than just failing. `jetwhale.getStatus` also reports the whole
permission state, so it can check before trying.

### Lifting every permission for one launch

Starting the host with `--mcp-allow-all-permissions` allows everything for that process only:

```shell
./gradlew runJetWhale --args="--mcp-allow-all-permissions"
```

This is for automated QA, where a run that has to enable a plugin or restart a server would
otherwise stop at a checkbox nobody is there to tick. Nothing is written back, so your own host keeps
whatever you chose, and the settings screen and `jetwhale.getStatus` both show the lifted state
rather than disagreeing with what the agent can actually do. Setting it requires being able to start
the host process — already more than the unauthenticated MCP port grants — so it opens no door that
was closed to that caller.

## Installing plugins from an AI agent

`jetwhale.installOfficialPlugin` is deliberately narrow. It accepts only a `pluginId` from the
**official catalog** — there is no MCP tool that installs arbitrary Maven coordinates — and it lives
in the **Manage plugins** group, which is off by default.

Installing does not enable: the sequence is

1. `jetwhale.installOfficialPlugin`
2. `jetwhale.setPluginEnabled`
3. **reconnect the MCP client** — tool lists are computed when a client connects, so a newly enabled
   plugin's own tools only appear on the next connection.

## Plugin-provided tools

Host plugins can expose their own MCP tools by implementing `JetWhaleMcpCapablePlugin`. Their tools
are registered alongside the built-ins; JetWhale automatically injects a required `sessionId`
parameter into each plugin tool's schema so an AI agent can target a specific connected device with
your custom debugging features too. See
[Developing Plugins → Exposing MCP tools](/guide/developing-plugins#exposing-mcp-tools) for how to
write one.

The [Network Inspector](/guide/network-inspector#mcp-tools) ships a full set of plugin tools
(`com.kitakkun.jetwhale.network.*`) for reading captured traffic and managing mock rules.

::: tip Sensitive values
Plugin UIs can hide sensitive content from `jetwhale.screenshot` **and**
`jetwhale.getAccessibilityTree` — both raise the same `LocalIsMcpCapture` CompositionLocal,
because the semantics tree carries the same strings the pixels do. The Network Inspector's
[redaction rules](/guide/network-inspector#redacting-sensitive-values) support an `MCP_ONLY` scope
that keeps values visible to you but hidden from AI agents.
:::

::: warning The MCP port is unauthenticated
The SSE endpoint has no authentication, so **any process on the machine** can reach it — and it is
no longer a read-only surface: the host tools change settings and can restart the debug server. Bear
that in mind before exposing the port beyond localhost.
:::

::: warning
The MCP server is experimental — tool names and behavior may change between releases.
:::
