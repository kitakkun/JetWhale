---
name: plugin-qa
description: Drive a JetWhale host plugin's real UI from an agent — launch the debug tool, then click/drag/type/screenshot the plugin through its MCP server to verify layout, gestures, persisted state, and restart restore.
---

# Plugin QA

How to verify a host plugin's UI **for real** instead of stopping at "it compiles and the unit tests
pass". The JetWhale debug tool exposes an MCP server whose tools operate on a plugin's Compose
scene, so an agent can drive the actual UI and look at the result.

The key property: the host creates a plugin's scene **on demand**, independent of what its window is
displaying. Driving a plugin never needs the window, and never steals it from whoever is using it —
`jetwhale.navigate` exists for the times you *want* the window moved, and is the only tool that
touches it.

**Assumed setup.** Your plugin module applies the `com.kitakkun.jetwhale.host` Gradle plugin and
sets `jetwhalePlugin.hostVersion`, which is what gives you the `runJetWhale` and
`runJetWhaleQaAgent` tasks used below.

## 1. Scope — what this can and cannot verify

Can:

- layout and rendering (screenshot), at a chosen viewport
- gestures: click, drag, scroll, typing, and the state changes they cause
- the Compose semantics tree (`getAccessibilityTree`) for locating elements
- persisted plugin state, and whether it survives a host restart
- the plugin's own contributed MCP tools
- **the host shell itself** — enabling a plugin, changing settings, restarting the debug server, and
  switching the main window to another screen, through the host tools in §4, once the launch grants
  them (see `--mcp-allow-all-permissions` there)

Cannot:

- **popout windows** — a plugin can be sent to the main window, but nothing pops it out or docks it
  back.
- **anything that needs the user's consent, on a host you did not launch.** The tools that change
  something are permission-gated: on a host started without `--mcp-allow-all-permissions` they are
  not in the tool list at all, and nothing the agent can call turns them on — a human grants them in
  **Settings → AI Agents → Permissions**. Launching the host yourself (§2) is the QA route around
  it; attaching to someone else's is not.
- the mouse cursor's appearance. An AWT cursor is not part of the rendered scene, so
  `Modifier.pointerHoverIcon` results never show up in a screenshot. Cover that with a unit test
  (see §7) and say plainly that the visual was not verified.
- native window chrome, real DPI switching, multi-window behaviour.

## 2. Launch the host

```bash
./gradlew runJetWhale \
  --args="--server-port 5081 --wss-port 5444 --mcp-server-port 7081"
```

`runJetWhale` downloads the released host matching `jetwhalePlugin.hostVersion` and launches it with
your plugin staged. `runJetWhaleHot` is the same on the JetBrains Runtime, and re-stages the plugin
in the background.

**Name the ports, every time.** A host that cannot bind them still starts, and nothing about the run
log says so — it just looks like an ordinary host that has no sessions yet, while the ports, and
therefore every debuggee and the MCP server on them, stay with whoever started first. From there you
are reading one host's window against another host's MCP server, and the disagreement
(`listSessions` says three, the window shows one) looks exactly like a bug in whatever you were
testing. Assume a host is already running: another checkout, another agent, a window left open
yesterday.

`--args` is `JavaExec`'s own task option and reaches the host's argument parser unchanged. It is the
only route — the host launch tasks define no `-P` property for arguments, unlike the QA agent's
`-PjetwhaleQaAgentArgs` in §3. An override holds for that launch only and is never written to the
sandbox's settings. Defaults, for reference, are `5080` / `5443` / `7080`.

Before believing anything the host reports — a session count, an empty selector, a screenshot —
confirm that the process holding your ports is yours:

```bash
pgrep -f com.kitakkun.jetwhale.host.MainKt | wc -l
lsof -nP -iTCP:5081,5444,7081 -sTCP:LISTEN   # the ports you asked for
```

Several host processes are normal and not a problem in themselves. What matters is which PID holds
your ports: if it is not the process your launch started, every observation downstream of it
describes someone else's host.

- Run it in the **background, redirected to a file** (`> run.log 2>&1`). Do NOT pipe it through
  `tail`/`grep`: the pipeline buffers and the log file stays empty until the process exits, so you
  lose all live output.
- Wait for the **MCP port to answer**, not for a process to appear — Gradle configuration plus the
  app launch takes minutes on a cold build, and the JVM exists long before the port opens. Waiting on
  `pgrep` means guessing how much longer with a `sleep`, and on a machine running several checkouts
  it matches somebody else's host and returns immediately.

  Bound the wait and watch the launcher: a build that fails or a port already taken otherwise leaves
  you spinning until a timeout with nothing to read.

  ```bash
  nohup ./gradlew runJetWhale \
    --args="--server-port 5081 --wss-port 5444 --mcp-server-port 7081" > run.log 2>&1 &
  LAUNCHER=$!
  DEADLINE=$((SECONDS + 900))
  until nc -z 127.0.0.1 7081 >/dev/null 2>&1; do
    kill -0 $LAUNCHER 2>/dev/null || { echo "launcher exited"; tail -n 30 run.log; break; }
    [ $SECONDS -ge $DEADLINE ] && { echo "timed out"; tail -n 30 run.log; break; }
    sleep 3
  done
  ```

  Readiness far sooner than a cold build takes is a warning, not a win: it usually means the port was
  already open. Check the PID against the `lsof` above before believing it.
- **Waiting on a Gradle build is a different problem, with a trap of its own.** `pgrep -f "<pattern>"`
  matches the polling shell too — its own command line contains the pattern — so an
  `until ! pgrep -f …` loop never exits and reports "still building" forever, including after the
  build has finished. Wait on the log instead, which also tells you the outcome:

  ```bash
  ./gradlew build … > build.log 2>&1 &
  until grep -qE "BUILD SUCCESSFUL|BUILD FAILED" build.log; do sleep 10; done
  ```

  Believe a stalled log only after checking the daemon: `ps -o %cpu= -p $(pgrep -f GradleDaemon)`
  distinguishes a long task from a hang.
- **Do not verify with a bare `./gradlew build`.** It drags in `:demo:shared:linkReleaseFrameworkIos*`
  and `:demo:android:{assembleRelease,lintVitalRelease}`; Kotlin/Native release linking is fully
  optimised and can turn a three-minute check into twenty. Debug output proves the change compiles:

  ```bash
  ./gradlew build -x :demo:web:build \
    -x :demo:shared:linkReleaseFrameworkIosArm64 \
    -x :demo:shared:linkReleaseFrameworkIosSimulatorArm64 \
    -x :demo:android:assembleRelease -x :demo:android:lintVitalRelease
  ```
- App data lives in an isolated sandbox, `<module>/build/jetwhale-sandbox`, not the developer's
  `~/.jetwhale`. **`clean` wipes it** — never clean between the save and restore halves of a
  persistence check.
- The plugin is staged to `<module>/build/jetwhale/devPlugins` and hot-reloaded from there.
- **Renaming `pluginArchiveName` leaves the old jar behind.** `stageDevPlugin` copies the new
  archive in but removes nothing, so the directory ends up with two jars declaring the **same
  pluginId** and the host loads one of them — in practice the stale one, which shows up as a rename
  that "did not take" while the manifest on disk plainly has the new name. Delete the old jar; the
  watcher does not react to a deletion, so restart the host afterwards.

  ```bash
  ls <module>/build/jetwhale/devPlugins   # expect exactly one jar
  ```

## 3. Get a debuggee you can actually drive

Every UI tool needs a `sessionId`, which means a connected debuggee. Your own app is a poor fit for
an agent-driven run: it fires only what its buttons are wired to, and the built-in tools cannot
drive it — `jetwhale.click` and friends reach plugin scenes, never a debuggee.

Use the headless QA agent instead. It connects as an ordinary session and exposes a control API, so
messages can be injected on demand. Name the plugin ids it should impersonate:

```bash
./gradlew runJetWhaleQaAgent \
  -PjetwhaleQaAgentArgs="--plugin com.example.myplugin --port 5444 --control-port 7101"
```

The agent version follows `jetwhalePlugin.hostVersion`, so agent and host speak the same protocol;
override it with `jetwhalePlugin.qaAgentVersion` if you need to.

**Pin these ports too.** `--port` is the host's **`--wss-port`**, not its `--server-port`: the agent
connects over wss and fetches the CA from that same port, so it is `5444` here. Leave it at the
default `5443` while your host is on something else and it will connect, quite happily, to a
*different* host — §2's failure arriving from the other end. `--control-port` (default `7100`) is
the agent's own API. That one does fail loudly — `BindException: Address already in use`, and the
process exits — but it prints its `control API on …` banner *before* binding, so a log skimmed for
that line is not evidence it came up. Give yours its own port and address it in every `curl` below;
otherwise a dead agent leaves you driving someone else's debuggee through theirs.

Background it — it holds the session open for as long as it runs.

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
curl -s 127.0.0.1:7101/send -H 'Content-Type: application/json' -d '{
  "pluginId": "com.example.myplugin",
  "messageType": "com.example.myplugin.protocol.ItemAdded",
  "payload": {"id": 1, "label": "hello"}
}'
```

`/request` takes the same shape plus an optional `timeoutMs` and returns the host's reply. Get the
`serialName` wrong and the host reports no registered handler — check it against your protocol
module rather than guessing.

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
curl -s 127.0.0.1:7101/send -H 'Content-Type: application/json' \
  -d '{"app":"checkout","pluginId":"com.example.myplugin","messageType":"…","payload":{}}'
```

**Disconnecting one app.** `POST /disconnect {"app":"checkout"}` gives up that app's session alone
and leaves the others connected — the way to exercise what the host does when a debuggee goes away
(does the selector fall back, does the scene survive, is per-session state kept or dropped?) without
killing the process. It is one-way: a stopped session cannot be revived, so restart the agent when
you need the app back. `POST /shutdown` still stops everything.

**This is send-only.** Inbound handlers resolve their serializer from a reified type parameter
(`onEvent<E>` / `onRequest<REQ, R>`), so there is no raw shape to register a catch-all against: a
plugin whose flow depends on the *host* requesting the agent cannot be answered from here. Drive
those from the plugin's own MCP tools instead.

- Use **`127.0.0.1`, not `localhost`** — the control API binds loopback IPv4 only, and `localhost`
  can resolve to `::1` first and get connection-refused.
- It shows up under `QA Agent (headless)`, with `appName` per `--app` (`qa-agent` by default), so it
  is never mistaken for a real device. Each app's `sessionId` is its own — per-session state is per
  session.
- `GET /plugins` lists what it is impersonating, with each one's `activated` / `ready` state
  aggregated over the still-connected apps plus an `apps` breakdown; `GET /health` carries the same
  split for the apps themselves, and `ready` there means *every connected app* is ready.
  `POST /shutdown` stops it.
- `POST /fire` (`{"method":"GET","url":"…"}`) injects real HTTP traffic for the bundled Network
  Inspector — the one plugin-specific shortcut, and it needs no `--plugin`. The client is
  instrumented per app, so the traffic is recorded against the app you addressed.
- On startup the agent logs a failed plain-HTTP CA fetch followed by a successful HTTPS one. That
  is the trust-on-first-use probe, not an error.

### When the plugin's own tools reach the app

The paragraph above is about the **built-in** tools. A plugin that contributes tools which act on
the debuggee — reading its UI, invoking something in it — turns a real app into a drivable debuggee
after all, through `<pluginId>.*`. For such a plugin the headless agent is often the wrong debuggee
entirely: a plugin that reads a running UI has nothing to read from an agent that has none.

Running against a real app costs a device, and `adb` is where the time goes:

- **A wedged adb server looks like an absent device.** A server that accepts the connection and
  never answers leaves every client printing `ADB server didn't ACK` / `failed to start daemon`.
  Probe it rather than guessing — a `host:version` request that times out is the proof:

  ```bash
  python3 -c 'import socket;s=socket.create_connection(("127.0.0.1",5037),5);s.settimeout(5);s.sendall(b"000chost:version");print(s.recv(64))'
  ```

  Do not kill somebody else's server: start your own on another port with
  `ANDROID_ADB_SERVER_PORT=5038` and pass it to everything, the `android` CLI included.
- **Your own server will not rediscover an emulator whose transport dropped.** `adb devices` goes
  empty and stays empty while the emulator is still running, so check for the process before
  concluding anything, then reattach:

  ```bash
  pgrep -f qemu-system | wc -l          # still alive?
  adb connect localhost:5555            # reattach; the serial is now localhost:5555, not emulator-5554
  ```

  Starting a second emulator for the same AVD does not work and wastes minutes on a lock.
- **A tool that reads the device through the default server reads nothing** once you are on a
  private one. `android layout` fails quietly and leaves its previous output file in place, so a
  comparison against it silently compares today's UI with a stale screen. Pass the server port
  through, and delete the output file before each run.

Everything else in this document applies unchanged — the plugin's own tools take the same
`sessionId` the built-ins do, unless the plugin declares `"scope": "host"` in its manifest. A
host-scoped plugin has one instance for the whole host, so its tools take **no `sessionId`** and can
be called with no app connected.

## 4. Reaching the tools

The host's MCP server is SSE on `http://localhost:<mcp-server-port>/sse`, with **no
authentication**. Register it and the tools arrive natively as `mcp__jetwhale__*` — no client script
needed. Its port must be the `--mcp-server-port` §2 launched with, or the tools answer for another
host without ever saying so:

```json
{
  "mcpServers": {
    "jetwhale": { "type": "sse", "url": "http://localhost:7081/sse" }
  }
}
```

`jetwhale.getStatus` reports the ports the host it answered for is running on; compare them with the
ones you passed before reading anything else into the result.

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
a human for. **The ones that change something are permission-gated and absent by default** — a
`tools/list` on a stock host shows only the read-only members of the table below, and calling a
missing one comes back `Tool jetwhale.setPluginEnabled not found` rather than a refusal that names
the reason. Grant them for the launch with `--mcp-allow-all-permissions`:

```bash
./gradlew runJetWhale \
  --args="--server-port 5081 --wss-port 5444 --mcp-server-port 7081 --mcp-allow-all-permissions"
```

The flag holds for that launch only; a human grants them per group in **Settings → AI Agents →
Permissions**.

The two kinds of gate fail differently, so read the symptom:

| Gate | Symptom |
|---|---|
| a **host** group (observe / navigate / manage plugins / settings & servers) | decidable up front, so a denied group's tools are **not listed** — the call fails with `Tool … not found` |
| a **plugin** permission (a plugin's UI, or its own `<pluginId>.*` tools) | the same tool is allowed for one plugin and denied for another, so it **stays listed** and the call comes back refused, naming the setting |

List the tools before planning around one, and do not read a refusal as a bug in the plugin.

| Tool | Purpose |
|------|---------|
| `jetwhale.getStatus` | version, both servers, session/plugin counts, settings, current screen — call it first |
| `jetwhale.listInstalledPlugins` | what is installed and whether it is enabled, plus the official catalog under `availableOfficial` |
| `jetwhale.installOfficialPlugin` | install a plugin from that catalog — catalog entries only, and `setPluginEnabled` still has to follow |
| `jetwhale.setPluginEnabled` | enable the plugin under test; reports which sessions got an instance |
| `jetwhale.navigate` | move the main window (`HOME` / `PLUGIN` / `SETTINGS` / `INFO` / `LOG_VIEWER`) |
| `jetwhale.getLogs` / `jetwhale.clearLogs` | the **host's** own log — clear, reproduce, read |
| `jetwhale.updateSettings` / `jetwhale.restartDebugServer` | ports and server lifecycle |

`getLogs` reads the host's log, not the debuggee's — it is how a plugin jar that failed to load
explains itself.

**These two invalidate what you are holding.** `restartDebugServer`, and `updateSettings` when it
changes a ws/wss setting, drop **every** session: each `sessionId` you hold goes stale and each
debuggee has to reconnect. Re-run `listSessions` afterwards. Changing `mcpServerPort` never restarts
the MCP server — it would kill your own connection — so that one only takes effect on the next host
start. `updateSettings` also *retires* the matching `--args` override from §2 for the rest of the
session, so a port set this way is the port from then on — pick one nobody else holds.

Always `Read` the screenshot afterwards. A screenshot you never open verifies nothing.

## 5. Coordinates and density — the expensive gotcha

- `click` / `drag` / `scroll` coordinates are in the **same pixel space as the screenshot**, so
  read positions off the image you just captured.
- `width` / `height` on `screenshot` are **pixels**, and the scene renders at the host window's
  density — 2.0 on a Retina Mac, so a 240dp minimum width measures 480px. That holds even for a
  scene the tool created on demand and the window never displayed: it is seeded with the window's
  density. Never assume 1 px = 1 dp.
- A pixel landmark is therefore only portable if you **pin the density**: `screenshot` takes a
  `density` argument — pass `2` and the geometry is identical on any machine. Omit it to inherit
  whatever the window is at. Values that are not finite and > 0 are rejected.
- `width`/`height`/`density` are **scoped to the capture** — the scene is put back on the viewport
  it was showing at before the tool returns, so the on-screen UI is untouched and the next
  screenshot does not inherit them. Still omit them to capture as-is unless a fixed viewport is the
  point.
- Prefer asserting **relationships** — "the left pane grew", "the divider stopped moving" — over
  absolute pixel values.

## 6. Verifying persisted state

`rememberPersistent` writes to:

```
<module>/build/jetwhale-sandbox/plugin-data/<pluginId>/store.json
```

- Writes are **debounced 300 ms**, so sleep 1–2 s after the interaction before reading the file.
- Restart restore is a real check and worth doing: kill the host, relaunch **with the same
  `--args` ports**, screenshot, and confirm the UI comes back at the persisted value rather than the
  coded default. **Wait for the loading spinner to clear** — the first frames after restart are a
  spinner, not your UI.

## 7. Prefer a regression test to a one-off check

If the behaviour can be pinned in a unit test, add the test as well — a manual MCP pass proves it
worked once, on one machine. A plugin's UI is ordinary Compose, so `compose-ui-test` reaches it
directly, without the host in the way.

Mutation-check every new test: break the production code, confirm the test fails, restore. A test
that passes both ways is worse than no test.

## 8. Reporting

State separately what was **driven and observed**, what was covered by **tests**, and what was
**not verified** (§1 lists the usual suspects). Do not let a green build imply the UI was seen.
