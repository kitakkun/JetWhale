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

The server speaks MCP over **SSE**, bound to **localhost** on port **7080** by default (configurable
in **Settings → AI Agents → MCP Server**):

- `GET http://localhost:7080/sse` — the SSE stream
- `POST http://localhost:7080/message?sessionId=...` — client-to-server messages

For example, to register it with Claude Code:

```shell
claude mcp add --transport sse jetwhale http://localhost:7080/sse
```

The host's **Settings → AI Agents → MCP Server** page shows this command — and an equivalent JSON
config block for other MCP clients — already filled in with the port the server is actually running
on, ready to copy.

::: tip Two different things called `sessionId`
The `sessionId` in the `/message` query string is the **MCP transport** session — one per SSE
connection, minted by the MCP library. It has nothing to do with the JetWhale **debug** session ids
that `jetwhale.listSessions` returns and the tools below take as arguments.
:::

The tool list is computed **when a client connects** and never changes for that connection, so a
plugin enabled (or a permission re-allowed) mid-session only shows up after the client reconnects.

## Discovery tools

Every other tool is addressed to one plugin in one session, and these are where those two ids come
from. Start here.

| Tool | What it does |
|------|--------------|
| `jetwhale.listSessions` | Lists connected debug sessions. Takes no arguments |
| `jetwhale.listPlugins` | Lists the plugins available in a session. Takes a `sessionId` |

## Plugin UI tools

| Tool | What it does |
|------|--------------|
| `jetwhale.screenshot` | Captures the current rendered frame of a plugin's Compose UI as a PNG |
| `jetwhale.click` | Dispatches a mouse click at pixel coordinates in a plugin's UI |
| `jetwhale.type` | Types text or a special key into a plugin's UI |
| `jetwhale.scroll` | Dispatches a scroll event in a plugin's UI |
| `jetwhale.drag` | Simulates a drag gesture in a plugin's UI |
| `jetwhale.getAccessibilityTree` | Returns the Compose semantics (accessibility) tree of a plugin's UI |

Because a single host can debug multiple apps at once — each with several plugins — every one of
these takes required `sessionId` **and** `pluginId` parameters: call `jetwhale.listSessions` first,
then `jetwhale.listPlugins` to pick the plugin.

### Parameters

Beyond that pair, each tool takes:

| Tool | Parameter | Required | Default | Meaning |
|------|-----------|----------|---------|---------|
| `screenshot` | `width`, `height` | no | the UI's current size (fallback 1280×720) | Render at a different size for this capture only. Must be supplied **together**. |
| | `density` | no | the scene's own density | e.g. `2` for HiDPI. Must be finite and greater than 0. |
| `click` | `x`, `y` | yes | — | Pixels from the plugin UI's left/top edge. |
| `type` | `text` | no | — | Printable characters to type. |
| | `specialKey` | no | — | A key name instead of text. **Exactly one** of `text` / `specialKey` must be given. |
| `scroll` | `x`, `y` | yes | — | Where to scroll. |
| | `deltaX` | no | `0` | Positive scrolls right, negative left. |
| | `deltaY` | no | `0` | Positive scrolls down, negative up. |
| `drag` | `startX`, `startY`, `endX`, `endY` | yes | — | The gesture's endpoints, in pixels. |
| | `steps` | no | `10` | Intermediate move events between them. |

`specialKey` accepts (case-insensitively): `ENTER`, `BACKSPACE`, `DELETE`, `TAB`, `ESCAPE`, `UP`,
`DOWN`, `LEFT`, `RIGHT`, `HOME`, `END`, `PAGE_UP`, `PAGE_DOWN`. There are no modifier parameters.

Worth knowing when a call does not do what you expect:

- **`jetwhale.click` is not a raw pointer event.** It finds the deepest clickable node containing the
  point and invokes its `OnClick` semantics action, so a point with nothing clickable under it comes
  back as *No clickable element found*, rather than silently doing nothing.
- **`jetwhale.type`'s `text` goes to the first editable node in the scene**, not to whatever has
  focus. Use `specialKey` (or a click first) when the target matters. A special key is dispatched as
  a real key-down/key-up pair.
- `jetwhale.drag` is for drag-and-drop gestures; use `jetwhale.scroll` to scroll a list.

`jetwhale.getAccessibilityTree` returns each node's `id`, `role`, `text`, `contentDescription`,
`bounds` (relative to the plugin UI's root) and the `isClickable` / `isEnabled` / `isFocused` /
`isSelected` / `isChecked` / `isEditable` flags, nested by `children`. Both it and
`jetwhale.screenshot` raise the plugin's `LocalIsMcpCapture`, so
[redaction](#plugin-provided-tools) applies to either.

::: tip Reading the *app's* UI, not the plugin's
These tools read the **host window's** Compose UI. To read the debugged app's own Compose tree — and
to invoke a node's action there rather than aiming at pixels — use the
[Compose Semantics Inspector](/guide/compose-semantics-inspector#mcp-tools).
:::

## Host tools

These target the debug tool itself rather than one plugin instance. `jetwhale.getStatus` is the
recommended first call: one parameter-free request tells an agent the host version, both servers'
endpoints, how many sessions and plugins are live, the current settings, and what the window is
showing.

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

None of them takes the required `sessionId` + `pluginId` pair the plugin UI tools route on. Some do
take a `pluginId` or an optional `sessionId` as ordinary arguments — which plugin to enable, which
session to open — but the tool is not scoped to them.

`jetwhale.getLogs` reads the **host's** log, not the debuggee app's — use it to diagnose JetWhale
itself, for example a plugin jar that failed to load. It takes `limit` (default 200, maximum 1000),
`level` and `contains` (a case-insensitive substring), and answers oldest-first with the matched and
total counts. Individual messages longer than 2000 characters are truncated.

`level` is `INFO` or `ERROR` — the buffer is the host's captured **stdout** and **stderr**, which is
all the distinction there is. It has nothing to do with the `--log-level` startup option, which sets
the root logger's threshold rather than choosing between these two streams.

### `jetwhale.updateSettings`

Every argument is optional; only the ones you supply are touched.

| Argument | Type | What it changes |
|----------|------|-----------------|
| `serverPort` | integer | The plain **ws** debug server port. |
| `wssPort` | integer | The **wss** port. |
| `wssEnabled` | boolean | Whether the wss connector is exposed at all. |
| `mcpServerPort` | integer | This server's port. Persisted only — see the warning below. |
| `adbAutoPortMappingEnabled` | boolean | [ADB auto port mapping](/guide/adb-auto-port-mapping). |
| `checkForUpdatesOnStartup` | boolean | The startup update check. |
| `persistData` | boolean | Whether captured debug data survives a host restart. |
| `restartDebugServer` | boolean | Whether to restart the debug server now. Defaults to `true` when any of `serverPort`, `wssPort`, `wssEnabled` or `adbAutoPortMappingEnabled` changed. |

Ports are validated (`1..65535`) before anything is written, and a call that supplies no settings at
all is rejected. The result reports exactly which keys were applied, whether the debug server was
restarted, and notes explaining any deferred effect.

### `jetwhale.navigate`

| Argument | Required | Accepted values |
|----------|----------|-----------------|
| `destination` | yes | `HOME`, `PLUGIN`, `SETTINGS`, `INFO`, `LOG_VIEWER` |
| `pluginId` | for `PLUGIN` | An installed, **enabled** plugin id. |
| `sessionId` | no | Only for `PLUGIN`; defaults to the session already selected in the drawer. |
| `settingsSection` | no | Only for `SETTINGS`: `GENERAL`, `SERVER`, `AI_AGENTS`, `PLUGINS`. Defaults to `GENERAL`. `SERVER` is the page the window titles **Connection**. |

Navigating to `PLUGIN` also selects that session in the drawer, which is what a subsequent
`jetwhale.screenshot` of the same plugin will show. The call waits up to two seconds for the window
to confirm, and reports `applied: false` with a reason if it does not.

`jetwhale.getStatus` can report destinations the tool cannot request — `DISABLED_PLUGIN`, `LICENSES`
and `MCP_TOOLS`. The tools browser in particular exists so a *person* can watch what an agent is
doing, so an agent has no reason to send itself there.

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
your custom debugging features too. A **host-scoped** plugin is the exception: it has a single
instance that belongs to no session, so its tools take **no `sessionId`** and work with nothing
connected at all. See
[Developing Plugins → Exposing MCP tools](/guide/developing-plugins#exposing-mcp-tools) for how to
write one.

The [Network Inspector](/guide/network-inspector#mcp-tools) ships a full set of plugin tools
(`com.kitakkun.jetwhale.network.*`) for reading captured traffic and managing mock rules, and the
[Compose Semantics Inspector](/guide/compose-semantics-inspector#mcp-tools) contributes
`com.kitakkun.jetwhale.semantics.*` for reading the **debuggee's** Compose node tree and invoking a
node's own action — the structural counterpart to the coordinate-based `jetwhale.click` above.

A plugin's `mcpCommands` list is read **once per plugin instance activation** and treated as static
for that instance's lifetime, so a list that changes at runtime is not picked up.

::: tip Sensitive values
Plugin UIs can hide sensitive content from `jetwhale.screenshot` **and**
`jetwhale.getAccessibilityTree` — both raise the same `LocalIsMcpCapture` CompositionLocal,
because the semantics tree carries the same strings the pixels do. The Network Inspector's
[redaction rules](/guide/network-inspector#redacting-sensitive-values) support an `MCP_ONLY` scope
that keeps values visible to you but hidden from AI agents.
:::

## The MCP tools browser

The host has its own view of what agents can do and what they have done. Open it from the **wrench
icon** at the top of the [drawer](/guide/host-window#the-rest-of-the-drawer), or from the **MCP
badge** on a plugin's drawer row — which lands you already filtered to that plugin.

Two filter rows across the top narrow everything below by **Plugin** and by **Session**. Each is a
multi-select: pick as many values as you like from the dropdown (it stays open so you can add
several), and remove one by clicking its chip. With nothing picked the filter reads *All*. A label at
the right says *MCP available*, or *MCP executing* while a call is running.

**Tools** — a searchable list of every tool in scope on the left (search matches the tool name, its
description, and the plugin name), and the selected tool's detail on the right: its short and fully
qualified names, the plugin that publishes it, its description, and each parameter with its type,
whether it is **required**, and its description. A trailing badge counts how many times that tool has
been called; while an agent is calling it, the badge takes an accent fill and a rotating ring.

**History** — the last 100 calls made in the current scope, newest first: the tool, the time of day,
and whether it succeeded. Selecting one shows the arguments it was called with and the response it
returned. Each section has an inline copy button, and **Copy details** takes the whole record — a
right-click on a history row offers the same copy actions (up to four; the argument and response
ones only appear when there are any).

This is the fastest way to answer "what did the agent actually send, and what did it get back?" when
a plugin behaves unexpectedly under automation.

::: warning The MCP port is unauthenticated
The SSE endpoint has no authentication, so **any process on the machine** can reach it — and it is
no longer a read-only surface: the host tools change settings and can restart the debug server. Bear
that in mind before exposing the port beyond localhost.
:::

::: warning
The MCP server is experimental — tool names and behavior may change between releases.
:::
