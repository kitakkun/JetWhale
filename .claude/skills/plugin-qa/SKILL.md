---
name: plugin-qa
description: Drive a JetWhale host plugin's real UI from an agent — launch the host, then click/drag/type/screenshot the plugin through the host's own MCP server to verify layout, gestures, persisted state, and restart restore.
---

# Plugin QA

How to verify a host plugin's UI **for real** instead of stopping at "it compiles and the unit
tests pass". The host exposes an MCP server whose tools operate on a plugin's Compose scene, so an
agent can drive the actual UI and look at the result.

The key property: `PluginComposeSceneService.getOrCreatePluginScene(pluginId, sessionId)` creates
the scene **on demand**, independent of what the host window is displaying. You never navigate the
host UI, and you never steal the window from whoever is using it.

## 1. Scope — what this can and cannot verify

Can:

- layout and rendering (screenshot), at a chosen viewport
- gestures: click, drag, scroll, typing, and the state changes they cause
- the Compose semantics tree (`getAccessibilityTree`) for locating elements
- persisted plugin state, and whether it survives a host restart
- the plugin's own contributed MCP tools

Cannot:

- **anything in the host shell** — settings, session switching, plugin install/enable, navigation,
  popout windows. Every tool is scoped to `pluginId` + `sessionId`; no host-control tool exists.
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
| `"activated": false` | the host has **not enabled this plugin id** — waiting will not help; enable it in the host, or check the id |
| `"activated": true, "ready": false` | connected, still preparing — keep polling |

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

**This is send-only.** Inbound handlers resolve their serializer from a reified type parameter
(`onEvent<E>` / `onRequest<REQ, R>`), so there is no raw shape to register a catch-all against: a
plugin whose flow depends on the *host* requesting the agent (the Network Inspector's mock replies,
for one) cannot be answered from here. Drive those from the plugin's own MCP tools instead.

- Use **`127.0.0.1`, not `localhost`** — the control API binds loopback IPv4 only, and `localhost`
  can resolve to `::1` first and get connection-refused.
- It shows up as `appName: qa-agent` / `QA Agent (headless)`, so it is never mistaken for a real
  device. Its `sessionId` is its own — mock rules and transactions are per session.
- `GET /plugins` lists what it is impersonating, with each one's `activated` / `ready` state;
  `POST /shutdown` stops it.
- `POST /fire` (`{"method":"GET","url":"…"}`) injects real HTTP traffic for the bundled Network
  Inspector — the one plugin-specific shortcut, and it needs no `--plugin`.
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
lists are also per-session — a plugin's own tools only register once its instance is live, so
reconnect after connecting a debuggee if you expect `<pluginId>.*` tools.

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
