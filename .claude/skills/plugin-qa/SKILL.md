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
  (see §6) and say plainly that the visual was not verified.
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

Every UI tool needs a `sessionId`, which means a connected debuggee. `./gradlew :demo:desktop:run`
is the cheapest source; the Android / iOS / web demos under `demo/` work too. If
`jetwhale.listSessions` comes back empty, nothing is connected — start a demo app first.

## 3. Reaching the tools

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

## 4. Coordinates and density — the expensive gotcha

- `click` / `drag` / `scroll` coordinates are in the **same pixel space as the screenshot**, so
  read positions off the image you just captured.
- `width` / `height` on `screenshot` are **pixels**, and `resolveViewport` keeps the scene's own
  density (`ScreenshotTool.kt:123`). That density comes from the host window — **2.0 on a Retina
  Mac, not 1.0**. So a 1280x720 request renders 640x360 **dp**, and a 240dp minimum width lands at
  480px. Never assume 1 px = 1 dp.
- Passing `width`/`height` **resizes the live scene** (`applyViewport` writes `composeScene.size`)
  — the very scene the host window is showing. Omit both to capture at the current size unless a
  fixed viewport is the point.
- Prefer asserting **relationships** — "the left pane grew", "the divider stopped moving" — over
  absolute pixel values, which depend on the machine's density.

## 5. Verifying persisted state

`rememberPersistent` writes to:

```
<module>/build/jetwhale-sandbox/plugin-data/<pluginId>/store.json
```

- Writes are **debounced 300 ms** (`RememberPersistent.kt`), so sleep 1–2 s after the interaction
  before reading the file.
- Restart restore is a real check and worth doing: kill the host, relaunch, screenshot, and
  confirm the UI comes back at the persisted value rather than the coded default. **Wait for the
  loading spinner to clear** — the first frames after restart are a spinner, not your UI.

## 6. Prefer a regression test to a one-off check

If the behaviour can be pinned in a unit test, add the test as well — a manual MCP pass proves it
worked once, on one machine. Scene-level test infrastructure already exists:
`jetwhale-host/core/mcp/src/test/kotlin/.../TestSceneFactory.kt` (`createTestScene`,
`renderTestScene`) plus the `dispatchClick` / `dispatchDrag` / `dispatchScroll` helpers.

Mutation-check every new test: break the production code, confirm the test fails, restore. A test
that passes both ways is worse than no test.

## 7. Reporting

State separately what was **driven and observed**, what was covered by **tests**, and what was
**not verified** (§1 lists the usual suspects). Do not let a green build imply the UI was seen.
