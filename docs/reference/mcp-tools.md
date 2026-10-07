# Built-in MCP Tools

The `jetwhale.*` tools the host's [MCP server](/guide/mcp-server) offers, with their arguments and
behavior. Tools that plugins contribute are listed on each plugin's page.

## Discovery tools

Every plugin tool is addressed to one plugin in one session, and these are where those two ids come
from. Start here.

| Tool | What it does |
|------|--------------|
| `jetwhale.listSessions` | Lists the debug sessions: first `host`, which is always there and holds the plugins that need no app, then every connected app. Takes no arguments |
| `jetwhale.listPlugins` | Lists the plugins available in a session. Takes a `sessionId` |

Neither is gated by a permission; see [Permissions](/guide/mcp-server#permissions).

## Plugin UI tools

These operate on a **plugin's UI inside the host**: capture it, click, type, scroll, and read its
semantics tree, so an agent can use any plugin the way you do.

| Tool | What it does |
|------|--------------|
| `jetwhale.screenshot` | Captures the current rendered frame of a plugin's Compose UI as a PNG |
| `jetwhale.click` | Invokes the `OnClick` action of the deepest clickable node at pixel coordinates in a plugin's UI; no pointer event is sent |
| `jetwhale.secondaryClick` | Sends a secondary-button (right) press and release at pixel coordinates, to open a context menu |
| `jetwhale.type` | Types text or a special key into a plugin's UI |
| `jetwhale.scroll` | Dispatches a scroll event in a plugin's UI |
| `jetwhale.drag` | Simulates a drag gesture in a plugin's UI |
| `jetwhale.getAccessibilityTree` | Returns the Compose semantics (accessibility) tree of a plugin's UI |

A host can debug several apps at once, each with several plugins, so every one of these takes
required `sessionId` **and** `pluginId` arguments: call `jetwhale.listSessions`, then
`jetwhale.listPlugins` to pick the plugin.

### Parameters

Beyond that pair, each tool takes:

| Tool | Parameter | Required | Default | Meaning |
|------|-----------|----------|---------|---------|
| `screenshot` | `width`, `height` | no | the UI's current size (fallback 1280×720) | Render at a different size for this capture only. Must be supplied **together**. |
| | `density` | no | the scene's own density | e.g. `2` for HiDPI. Must be finite and greater than 0. |
| `click` | `x`, `y` | yes | — | Pixels from the plugin UI's left/top edge. |
| `secondaryClick` | `x`, `y` | yes | — | Pixels from the plugin UI's left/top edge. |
| `type` | `text` | no | — | Printable characters to type. |
| | `specialKey` | no | — | A key name instead of text. **Exactly one** of `text` / `specialKey` must be given. |
| `scroll` | `x`, `y` | yes | — | Where to scroll. |
| | `deltaX` | no | `0` | Positive scrolls right, negative left. |
| | `deltaY` | no | `0` | Positive scrolls down, negative up. |
| `drag` | `startX`, `startY`, `endX`, `endY` | yes | — | The gesture's endpoints, in pixels. |
| | `steps` | no | `10` | Intermediate move events between them. |

`specialKey` accepts, case-insensitively: `ENTER`, `BACKSPACE`, `DELETE`, `TAB`, `ESCAPE`, `UP`,
`DOWN`, `LEFT`, `RIGHT`, `HOME`, `END`, `PAGE_UP`, `PAGE_DOWN`. There are no modifier parameters.

### Behavior

- **`jetwhale.click` is not a raw pointer event.** It finds the deepest clickable node containing the
  point and invokes its `OnClick` semantics action, so a point with nothing clickable under it comes
  back as *No clickable element found* rather than silently doing nothing.
- **`jetwhale.secondaryClick` is a raw pointer event**: a secondary-button press and release at the
  point, so a `ContextMenuArea` or a handler that checks for the secondary button reacts as it would
  to a right-click. It answers with `consumed` (whether a handler consumed the press or release),
  `openedPopup` and `closedPopup`, and `popupClickableNodes`: the clickable nodes of every popup
  still open, such as a menu's items, topmost popup first and in the node shape
  `jetwhale.getAccessibilityTree` returns. Pick an item with `jetwhale.click` at the center of its
  `bounds`. The nodes are left out when the plugin's UI may not be inspected (see
  [Permissions](/guide/mcp-server#permissions)). When nothing consumed the click and no popup opened,
  closed or is open, it comes back as an error; a handler that reacts without consuming the event is
  not detected, so check with a screenshot.
- **An open menu stays open until something closes it.** Picking an item closes it. To close it
  without picking one, `jetwhale.secondaryClick` outside it: like a real click outside a menu, that
  press only closes the menu (`closedPopup: true`) and reaches nothing beneath it. `jetwhale.click`
  outside the menu leaves it open: it invokes the clickable element beneath the point as if the menu
  were not there.
- **`jetwhale.type`'s `text` goes to the focused text field**, where a user's keystrokes would land,
  or to the first text field in the scene when none has focus. Click a field first when the target
  matters. A special key is dispatched as a real key-down/key-up pair.
- **`jetwhale.drag` is for drag-and-drop gestures**; use `jetwhale.scroll` to scroll a list.

`jetwhale.getAccessibilityTree` returns each node's `id`, `role`, `text`, `contentDescription`,
`bounds` (relative to the plugin UI's root) and the `isClickable` / `isEnabled` / `isFocused` /
`isSelected` / `isChecked` / `isEditable` flags, nested by `children`. An open popup or dialog,
such as a context menu, comes after the plugin's own content as a top-level node of its own, and
`jetwhale.screenshot` draws it over that content. The tree and the screenshot both raise the
plugin's `LocalIsMcpCapture`, so a plugin's
[sensitive-value hiding](/guide/developing-plugins#hiding-sensitive-ui-from-mcp-captures) applies to
either, and so does `jetwhale.secondaryClick` when it reads the items of a popup.

::: tip Reading the *app's* UI, not the plugin's
These tools read the **host window's** Compose UI. To read the debugged app's own Compose tree, and
to invoke a node's action there rather than aiming at pixels, use the
[Compose Semantics Inspector](/guide/compose-semantics-inspector#mcp-tools).
:::

## Host tools

These target the debug tool itself rather than one plugin instance. `jetwhale.getStatus` is the
recommended first call: one request with no arguments tells an agent the host version, both
servers' endpoints, how many sessions and plugins are live, the current settings, the permission
state, and what the window is showing.

| Tool | What it does |
|------|--------------|
| `jetwhale.getStatus` | One snapshot of the host: version, servers, session and plugin counts, settings, current screen |
| `jetwhale.getLogs` | Reads the host's own captured stdout/stderr, filterable by level and substring |
| `jetwhale.clearLogs` | Discards every captured host log entry |
| `jetwhale.listInstalledPlugins` | Lists installed plugins and their enabled state, official plugins still available, and any failed or untrusted jar |
| `jetwhale.setPluginEnabled` | Enables or disables an installed plugin, like the sidebar toggle |
| `jetwhale.installOfficialPlugin` | Installs a plugin from the official catalog; see [Installing plugins from an AI agent](/guide/mcp-server#installing-plugins-from-an-ai-agent) |
| `jetwhale.updateSettings` | Changes host settings; only the arguments you supply are touched |
| `jetwhale.restartDebugServer` | Restarts the debug WebSocket server |
| `jetwhale.navigate` | Switches the main window to another screen, selecting the session and plugin as it goes |

None of them takes the required `sessionId` + `pluginId` pair the plugin UI tools route on. Some take
a `pluginId` or an optional `sessionId` as ordinary arguments — which plugin to enable, which session
to open — but the tool is not scoped to them.

`jetwhale.getLogs` reads the **host's** log, not the debugged app's; use it to diagnose JetWhale
itself, for example a plugin jar that failed to load. It takes `limit` (default 200, maximum 1000),
`level` and `contains` (a case-insensitive substring), and answers oldest first with the matched and
total counts. Messages longer than 2000 characters are truncated. `level` is `INFO` or `ERROR`: the
buffer is the host's captured **stdout** and **stderr**, which is all the distinction there is. It has
nothing to do with the `--log-level` startup option, which sets the root logger's threshold.

### `jetwhale.updateSettings`

Every argument is optional; only the ones you supply are touched.

| Argument | Type | What it changes |
|----------|------|-----------------|
| `serverPort` | integer | The plain **ws** debug server port. |
| `wssPort` | integer | The **wss** port. |
| `wssEnabled` | boolean | Whether the wss connector is exposed at all. |
| `mcpServerPort` | integer | The MCP server's port. Persisted only; see the warning below. |
| `adbAutoPortMappingEnabled` | boolean | [ADB auto port mapping](/guide/adb-auto-port-mapping). |
| `persistData` | boolean | Whether captured debug data survives a host restart. |
| `restartDebugServer` | boolean | Whether to restart the debug server now. Defaults to `true` when any of `serverPort`, `wssPort`, `wssEnabled` or `adbAutoPortMappingEnabled` changed. |

Ports are validated (`1..65535`) before anything is written, and a call that supplies no settings is
rejected. The result reports which keys were applied, whether the debug server was restarted, and
notes explaining any deferred effect.

### `jetwhale.navigate`

| Argument | Required | Accepted values |
|----------|----------|-----------------|
| `destination` | yes | `HOME`, `PLUGIN`, `SETTINGS`, `INFO`, `LOG_VIEWER` |
| `pluginId` | for `PLUGIN` | An installed, **enabled** plugin id. |
| `sessionId` | no | Only for `PLUGIN`; defaults to the session already selected in the sidebar. |
| `settingsSection` | no | Only for `SETTINGS`: `GENERAL`, `SERVER`, `AI_AGENTS`, `PLUGINS`. Defaults to `GENERAL`. `SERVER` is the page the window titles **Connection**. |

Navigating to `PLUGIN` also selects that session in the sidebar, which is what a following
`jetwhale.screenshot` of the same plugin shows. The call waits up to two seconds for the window to
confirm, and reports `applied: false` with a reason if it does not.

`jetwhale.getStatus` can report destinations the tool cannot request: `DISABLED_PLUGIN`, `LICENSES`
and `MCP_TOOLS`.

::: warning Destructive host tools
`jetwhale.restartDebugServer`, and `jetwhale.updateSettings` when it changes a ws/wss setting, stop
the debug server, which disconnects **every** session. Each `sessionId` an agent holds becomes
invalid and each app has to reconnect. Pass `restartDebugServer: false` to `updateSettings` to
persist a change and apply it later instead.

Changing `mcpServerPort` never restarts the MCP server, because that would drop the agent's own
connection. The new port takes effect the next time the host starts.
:::
