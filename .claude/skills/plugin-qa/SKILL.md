---
name: plugin-qa
description: QA a host plugin from inside the JetWhale repository — the in-repo launch tasks, the locally built QA agent, and the host-side test infrastructure that plugin authors outside this repository do not have.
---

# Plugin QA (in-repo)

The workflow itself — MCP tools, the QA agent's control API, ports, density, persisted state,
reporting — is published as a skill for plugin authors and lives at
[`plugins/jetwhale/skills/plugin-qa/SKILL.md`](../../../plugins/jetwhale/skills/plugin-qa/SKILL.md).
**Read that first**; everything there applies here unchanged. Install it with
`/plugin install jetwhale@jetwhale` and it is available as `/jetwhale:plugin-qa`.

This file covers only what is different when the working tree *is* JetWhale.

## Launch the host from this build, not from a release

<<<<<<< HEAD
`runJetWhale` downloads the released host matching `jetwhalePlugin.hostVersion`, which is wrong when
you are changing the host. Use the in-repo launcher, which runs `:jetwhale-host:app` from the
working tree:
=======
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
- **anything the permissions tree denies.** Managing plugins and changing settings/servers are off
  by default, and the agent cannot grant itself anything — no tool writes permissions. A refusal
  names the group or plugin that blocked it; ask the user to tick it in
  **Settings → AI Agents → Permissions**. `jetwhale.getStatus` reports the whole
  permission state, so check there first when a QA run needs a group that is off.
- the mouse cursor's appearance. An AWT cursor is not part of the rendered scene, so
  `Modifier.pointerHoverIcon` results never show up in a screenshot. Cover that with a unit test
  (see §7) and say plainly that the visual was not verified.
- native window chrome, real DPI switching, multi-window behaviour.

## 2. Launch the host
>>>>>>> 61428e6d (feat(mcp): gate MCP tools behind a permission tree)

```bash
./gradlew :jetwhale-plugins:<plugin>:host:runJetWhaleLocal \
  --args="--server-port 5081 --wss-port 5444 --mcp-server-port 7081"
```

Ports still have to be named — see the published skill for why a host that cannot bind them starts
anyway and says nothing. That failure is *more* likely here, since several worktrees and agents
share this machine.

`:jetwhale-host:app:run` also works and skips the plugin staging, but it uses the developer's real
`~/.jetwhale` instead of a sandbox. Prefer `runJetWhaleLocal`.

## Run the QA agent from this build

```bash
./gradlew :jetwhale-plugins:<plugin>:host:runJetWhaleQaAgentLocal \
  -PjetwhaleQaAgentArgs="--plugin com.example.myplugin --port 5444 --control-port 7101"
```

`runJetWhaleQaAgentLocal` runs `:tools:qa-agent` from the working tree; the published
`runJetWhaleQaAgent` can only run a released one. Both read the same `-PjetwhaleQaAgentArgs`, so a
command line moves between them unchanged. `./gradlew :tools:qa-agent:run --args="…"` runs the same
binary without going through a plugin module.

`./gradlew :demo:desktop:run` connects the demo app when you specifically want its own fixed
requests rather than injected ones.

## Reach for a host-side test before a one-off MCP pass

Scene-level infrastructure exists here and nowhere else:
`jetwhale-host/core/mcp/src/test/kotlin/.../TestSceneFactory.kt` (`createTestScene`,
`renderTestScene`), driven with the `dispatchClick` / `dispatchDrag` / `dispatchScroll` helpers the
tools themselves expose (`ClickTool.kt`, `DragTool.kt`, `ScrollTool.kt`). A behaviour pinned there
survives; a manual MCP pass proves it worked once, on one machine.

Mutation-check every new test: break the production code, confirm the test fails, restore.

## Reading the implementation

When the published skill describes a behaviour and you need the mechanism, these are the files:

| Behaviour | Where |
|---|---|
| Ports on the command line | `jetwhale-host/app/.../cli/CommandLineArgumentsParser.kt` |
| Screenshot viewport and the `density` argument | `ScreenshotTool.kt`, `McpViewportUtils.kt` |
| A scene created on demand inheriting the window's density | `PluginComposeSceneService.updateHostDensity` |
| `rememberPersistent`'s 300 ms debounce | `RememberPersistent.kt` |
| The QA agent's control API | `tools/qa-agent/src/main/kotlin/…` |

`.mcp.json` is gitignored (`.gitignore:7`), so each developer creates their own.

## Keep the published skill true

The published skill documents behaviour this repository owns. A change to MCP tool names, the QA
agent's control API, or the launch tasks' arguments makes it wrong the moment it merges — update it
in the same PR, not later.
