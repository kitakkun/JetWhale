---
name: plugin-qa
description: Drive a JetWhale host plugin's real UI from an agent — launch the host, then click/drag/type/screenshot the plugin through the host's own MCP server to verify layout, gestures, persisted state, and restart restore.
---

# Plugin QA

How to verify a host plugin's UI **for real** instead of stopping at "it compiles and the unit
tests pass". The host exposes an MCP server whose tools operate on a plugin's Compose scene, so an
agent can drive the actual UI and look at the result.

The key property: `PluginComposeSceneService.getOrCreatePluginScene(pluginId, sessionId)` creates
the scene **on demand**, independent of what the host window is displaying. Driving a plugin never
needs the host window, and never steals it from whoever is using it — `jetwhale.navigate` exists for
the times you *want* the window moved, and is the only tool that touches it.

## 1. Scope — what this can and cannot verify

Can:

- layout and rendering (screenshot), at a chosen viewport
- gestures: click, drag, scroll, typing, and the state changes they cause
- the Compose semantics tree (`getAccessibilityTree`) for locating elements
- persisted plugin state, and whether it survives a host restart
- the plugin's own contributed MCP tools
- **the host shell itself** — enabling a plugin, changing settings, restarting the debug server, and
  switching the main window to another screen, through the host tools in §4

Cannot:

- **popout windows** — a plugin can be sent to the main window, but nothing pops it out or docks it
  back.
- **anything that needs the user's consent.** Installing a plugin is off by default and the agent
  cannot lift that gate itself: `jetwhale.updateSettings` deliberately does not expose
  `mcpPluginInstallAllowed`, so a refusal means asking the user to flip it in
  **Settings → Server → MCP Server**.
- the mouse cursor's appearance. An AWT cursor is not part of the rendered scene, so
  `Modifier.pointerHoverIcon` results never show up in a screenshot. Cover that with a unit test
  (see §7) and say plainly that the visual was not verified.
- native window chrome, real DPI switching, multi-window behaviour.

## 2. Launch the host

```bash
./gradlew :jetwhale-plugins:<plugin>:host:runJetWhaleLocal
```

- Run it in the **background, redirected to a file** (`> run.log 2>&1`). Do NOT pipe it through
  `tail`/`grep`: the pipeline buffers and the log file stays empty until the process exits, so you
  lose all live output.
- Wait for it properly — Gradle configuration plus the app launch takes minutes on a cold build:
  ```bash
  until pgrep -f com.kitakkun.jetwhale.host.MainKt >/dev/null; do sleep 3; done
  ```
- App data lives in an isolated sandbox, `<module>/build/jetwhale-sandbox`, not the developer's
  `~/.jetwhale`. **`clean` wipes it** — never clean between the save and restore halves of a
  persistence check.
- The plugin is staged to `<module>/build/jetwhale/devPlugins` and hot-reloaded from there.

## 3. Get a debuggee you can actually drive

Every UI tool needs a `sessionId`, which means a connected debuggee. The demo apps are a poor fit:
they only fire the handful of requests their buttons are wired to, and **a GUI app cannot be driven
from an agent at all** — MCP reaches plugin scenes, never a debuggee.

Use the headless QA agent instead. It connects as an ordinary session and exposes a control API, so
messages can be injected on demand. Name the plugin ids it should impersonate:

```bash
# in this repository, from the plugin module
./gradlew :jetwhale-plugins:<plugin>:host:runJetWhaleQaAgentLocal \
  -PjetwhaleQaAgentArgs="--plugin com.example.myplugin"

# from a plugin's own repository (jetwhalePlugin.hostVersion decides the agent version)
./gradlew runJetWhaleQaAgent -PjetwhaleQaAgentArgs="--plugin com.example.myplugin"
```

Background it — it holds the session open for as long as it runs. (`./gradlew :tools:qa-agent:run
--args="…"` runs the same binary without going through a plugin module.)

**Wait for readiness, and read it when a send fails.** The control API answers well before the debug
session is up, so a send right after the port opens is dropped. Poll `GET /health` until
`"ready": true`. When a send does come back `"sent": false`, its `hint` and `GET /plugins` separate
the two situations:

| `/plugins` | meaning |
|---|---|
| `"activated": false` | the host has **not enabled this plugin id** — waiting will not help. Enable it with `jetwhale.setPluginEnabled` (§4), or check the id |
| `"activated": true, "ready": false` | connected, still preparing — keep polling |
| both false for one app under `apps` | that app was disconnected (see below) — it will never come back |

**Driving the plugin.** `/send` and `/request` speak the messenger's raw layer, so the agent needs no
compile-time knowledge of the plugin. `messageType` is the payload class's `serialName` (its
fully-qualified name unless it carries `@SerialName`), and `payload` is that class as JSON:

```bash
curl -s 127.0.0.1:7100/send -H 'Content-Type: application/json' -d '{
  "pluginId": "com.example.myplugin",
  "messageType": "com.example.myplugin.protocol.ItemAdded",
  "payload": {"id": 1, "label": "hello"}
}'
```

`/request` takes the same shape plus an optional `timeoutMs` and returns the host's reply. Get the
`serialName` wrong and the host reports no registered handler — check it against the plugin's
protocol module rather than guessing.

**Several apps at once.** A session is *one app*, and the host groups sessions by device into a
two-level selector (pick a device, then an app under it). One `--app` per app gives you exactly that
shape from a single process — same device, several apps — which is what you need to check the
selector, per-session state isolation, and anything that must not leak between apps:

```bash
-PjetwhaleQaAgentArgs="--app checkout --app catalog --plugin com.example.myplugin"
```

Every app registers the same `--plugin` set but its own instances, so `checkout` and `catalog` are
independent sessions. Without `--app` you get the single `qa-agent` app as before, and every `app`
field below can be omitted; with several, calls that leave it out are refused rather than guessed at.

```bash
curl -s 127.0.0.1:7100/send -H 'Content-Type: application/json' \
  -d '{"app":"checkout","pluginId":"com.example.myplugin","messageType":"…","payload":{}}'
```

**Disconnecting one app.** `POST /disconnect {"app":"checkout"}` gives up that app's session alone
and leaves the others connected — the way to exercise what the host does when a debuggee goes away
(does the selector fall back, does the scene survive, is per-session state kept or dropped?) without
killing the process. It is one-way: a stopped session cannot be revived, so restart the agent when
you need the app back. `POST /shutdown` still stops everything.

**This is send-only.** Inbound handlers resolve their serializer from a reified type parameter
(`onEvent<E>` / `onRequest<REQ, R>`), so there is no raw shape to register a catch-all against: a
plugin whose flow depends on the *host* requesting the agent (the Network Inspector's mock replies,
for one) cannot be answered from here. Drive those from the plugin's own MCP tools instead.

- Use **`127.0.0.1`, not `localhost`** — the control API binds loopback IPv4 only, and `localhost`
  can resolve to `::1` first and get connection-refused.
- It shows up under `QA Agent (headless)`, with `appName` per `--app` (`qa-agent` by default), so it
  is never mistaken for a real device. Each app's `sessionId` is its own — mock rules and
  transactions are per session.
- `GET /plugins` lists what it is impersonating, with each one's `activated` / `ready` state
  aggregated over the still-connected apps plus an `apps` breakdown; `GET /health` carries the same
  split for the apps themselves, and `ready` there means *every connected app* is ready.
  `POST /shutdown` stops it.
- `POST /fire` (`{"method":"GET","url":"…"}`) injects real HTTP traffic for the bundled Network
  Inspector — the one plugin-specific shortcut, and it needs no `--plugin`. The client is
  instrumented per app, so the traffic is recorded against the app you addressed.
- On startup the agent logs a failed plain-HTTP CA fetch followed by a successful HTTPS one. That
  is the trust-on-first-use probe, not an error.

`./gradlew :demo:desktop:run` still works when you specifically want the demo's own fixed requests.

## 4. Reaching the tools

The host's MCP server is SSE on `http://localhost:7080/sse`, with **no authentication**. Register
it and the tools arrive natively as `mcp__jetwhale__*` — no client script needed. `.mcp.json` is
gitignored (`.gitignore:7`), so each developer creates their own:

```json
{
  "mcpServers": {
    "jetwhale": { "type": "sse", "url": "http://localhost:7080/sse" }
  }
}
```

The catch: MCP servers connect when the session starts, and this workflow **launches the host
mid-session**. If the tools are missing or stale, reconnect with `/mcp` after the host is up. Tool
lists are fixed at connection time, so a plugin's own tools appear only on a connection opened after
its instance went live — reconnect after connecting a debuggee, and again after
`jetwhale.setPluginEnabled`, if you expect `<pluginId>.*` tools.

If reconnecting is not an option (say, a headless run), the same server is reachable over plain
HTTP: open `GET /sse`, take the `endpoint` event's path, and POST JSON-RPC to it.

| Tool | Purpose |
|---|---|
| `jetwhale.listSessions` / `jetwhale.listPlugins` | discovery (read-only) |
| `jetwhale.screenshot` | render the plugin scene to PNG |
| `jetwhale.click` / `jetwhale.drag` / `jetwhale.scroll` | pointer input |
| `jetwhale.type` | text and special keys |
| `jetwhale.getAccessibilityTree` | semantics tree; use it to find coordinates |
| `<pluginId>.*` | tools the plugin itself contributes |

Host-scoped tools take neither `pluginId` nor `sessionId`, and cover the setup a QA run used to need
a human for:

| Tool | Purpose |
|------|---------|
| `jetwhale.getStatus` | version, both servers, session/plugin counts, settings, current screen — call it first |
| `jetwhale.listInstalledPlugins` | what is installed and whether it is enabled |
| `jetwhale.setPluginEnabled` | enable the plugin under test; reports which sessions got an instance |
| `jetwhale.navigate` | move the main window (`HOME` / `PLUGIN` / `SETTINGS` / `INFO` / `LOG_VIEWER`) |
| `jetwhale.getLogs` / `jetwhale.clearLogs` | the **host's** own log — clear, reproduce, read |
| `jetwhale.updateSettings` / `jetwhale.restartDebugServer` | ports and server lifecycle |

`getLogs` reads the host's log, not the debuggee's — it is how a plugin jar that failed to load
explains itself.

::: warning
`restartDebugServer`, and `updateSettings` when it changes a ws/wss setting, drop **every** session:
each `sessionId` you hold goes stale and each debuggee has to reconnect. Re-run `listSessions`
afterwards. Changing `mcpServerPort` never restarts the MCP server — it would kill your own
connection — so that one only takes effect on the next host start.
:::

Always `Read` the screenshot afterwards. A screenshot you never open verifies nothing.

## 5. Coordinates and density — the expensive gotcha

- `click` / `drag` / `scroll` coordinates are in the **same pixel space as the screenshot**, so
  read positions off the image you just captured.
- `width` / `height` on `screenshot` are **pixels**, and the scene renders at the host window's
  density — 2.0 on a Retina Mac, so a 240dp minimum width measures 480px. That holds even for a
  scene the tool created on demand and the window never displayed: it is seeded with the window's
  density (`PluginComposeSceneService.updateHostDensity`). Never assume 1 px = 1 dp.
- A pixel landmark is therefore only portable if you **pin the density**: `screenshot` takes a
  `density` argument (`ScreenshotTool.kt:52`) — pass `2` and the geometry is identical on any
  machine. Omit it to inherit whatever the window is at. Values that are not finite and > 0 are
  rejected.
- Passing `width`/`height`/`density` **mutates the live scene** — `applyViewport` writes
  `composeScene.density` and `composeScene.size` (`McpViewportUtils.kt:37`) on the very scene the
  host window is showing, and it keeps rendering that way until the window next resizes. Omit them
  to capture as-is unless a fixed viewport is the point.
- Prefer asserting **relationships** — "the left pane grew", "the divider stopped moving" — over
  absolute pixel values.

## 6. Verifying persisted state

`rememberPersistent` writes to:

```
<module>/build/jetwhale-sandbox/plugin-data/<pluginId>/store.json
```

- Writes are **debounced 300 ms** (`RememberPersistent.kt`), so sleep 1–2 s after the interaction
  before reading the file.
- Restart restore is a real check and worth doing: kill the host, relaunch, screenshot, and
  confirm the UI comes back at the persisted value rather than the coded default. **Wait for the
  loading spinner to clear** — the first frames after restart are a spinner, not your UI.

## 7. Prefer a regression test to a one-off check

If the behaviour can be pinned in a unit test, add the test as well — a manual MCP pass proves it
worked once, on one machine. Scene-level test infrastructure already exists:
`jetwhale-host/core/mcp/src/test/kotlin/.../TestSceneFactory.kt` (`createTestScene`,
`renderTestScene`), driven with the `dispatchClick` / `dispatchDrag` / `dispatchScroll` helpers the
tools themselves expose (`ClickTool.kt`, `DragTool.kt`, `ScrollTool.kt`).

Mutation-check every new test: break the production code, confirm the test fails, restore. A test
that passes both ways is worse than no test.

## 8. Reporting

State separately what was **driven and observed**, what was covered by **tests**, and what was
**not verified** (§1 lists the usual suspects). Do not let a green build imply the UI was seen.
