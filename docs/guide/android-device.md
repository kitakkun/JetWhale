# Android Device

Android Device is an official JetWhale plugin that turns a connected device or emulator into
[MCP](/guide/mcp-server) tools: find it, install and launch an app, tap and type at it, look at the
screen, read the log, reset its state. An AI agent can run a whole QA pass through it without ever
typing `adb`.

- 🔌 **29 tools** covering discovery, screen, input, apps, logs, files and ports
- 🎯 Every tool targets its device explicitly — `-s <serial>` is never left off, and with several
  devices connected an omitted `serial` is an error rather than a guess
- 🧾 Every result carries the exact adb argument vectors that ran, so a caller can audit what happened
- 🚫 No arbitrary `shell` tool: each tool validates its own input, which is the point

## Why not raw adb?

An agent driving `adb` by hand gets four things wrong over and over: it targets the wrong device
when two are connected, it taps coordinates that are not on the screen (which `input tap` accepts
silently and ignores), it sends text whose spaces and quotes the shell eats, and it reads a
`Failure [...]` line as success because the exit code was zero. Every tool here closes one of those:
the target is resolved before the tool body runs, coordinates are checked against `wm size`, text is
escaped for both the shell and `input text`, and install output is parsed rather than trusted.

## Setup

The plugin needs **no agent in your app**. The host runs one instance of it in the always-present
`host` session as soon as it is enabled, so its tools take `sessionId: "host"` and work before any
app connects — installing the app under test is itself one of them. It is headless, so it has no
screen in the host window.

Install it from the host's **official catalog**: **Settings → Plugins → Add Plugins → Official
Plugins**. See [Host Settings → Plugins](/guide/host-settings#plugins) for the other install routes.

It runs adb through the host, which looks adb up the same way it does for its own
[port mapping](/guide/adb-auto-port-mapping), so there is nothing to configure. If the host cannot
find an Android SDK, every tool answers with a failed result whose `error` says no adb was found,
rather than failing the MCP server.

## Choosing the device

Every tool except `listDevices` takes an optional `serial`:

| `serial` | What happens |
|---|---|
| Given | It must appear in `adb devices -l` in state `device`. An unknown serial, or one that is `offline` / `unauthorized`, is an argument error naming the serials that *are* known and their states. |
| Omitted, one device connected | That device is used. |
| Omitted, no device connected | Argument error: "no device is connected". |
| Omitted, several devices connected | Argument error listing the serials. Nothing is picked for you. |

## Reading a result

Every tool answers with JSON shaped like this:

```json
{
  "serial": "emulator-5554",
  "ok": true,
  "x": 540,
  "y": 1200,
  "adb": [
    ["devices", "-l"],
    ["-s", "emulator-5554", "shell", "wm", "size"],
    ["-s", "emulator-5554", "shell", "wm", "density"],
    ["-s", "emulator-5554", "shell", "input", "tap", "540", "1200"]
  ]
}
```

`adb` is the exact argument vectors that ran, in order — the audit trail. When the device refused
the command, the call fails: `ok` is `false`, and the result carries `error`, `exitCode`, and what
the command printed to stdout and stderr as `output` and `errorOutput`. Every adb call has a
timeout, and one that runs out fails the call with an `error` naming the command. A mistake in the
**call itself** (unknown serial, off-screen coordinate, text that cannot be typed, a path that does
not exist) fails with just a message instead, without anything having been sent to the device.

## Tools

All tool names are prefixed `com.kitakkun.jetwhale.androiddevice.`, and every call passes
`sessionId: "host"`. Coordinates are **screen pixels** unless `unit` is `DP`, in which case they are
converted with the device's density.

### Discovery and state

| Tool | Arguments | What it does |
|---|---|---|
| `listDevices` | — | Lists every device adb can see: `serial`, `state`, `model`, `product`, `transportId`, `isEmulator`. Start here. |
| `deviceInfo` | `serial?` | Model, manufacturer, Android release and SDK level, screen size and density, current rotation. |
| `waitForDevice` | `serial?`, `timeoutSeconds?` | Waits for the device to connect and finish booting (`sys.boot_completed=1`). Defaults to 60 seconds, because adb's own wait has no timeout at all. Without `serial` it waits for any device, then follows the same rule as every other tool: several connected devices are an error. |
| `wake` | `serial?` | Wakes the screen and dismisses the keyguard, so a screenshot shows the app. |
| `setRotation` | `serial?`, `rotation` | Pins the screen to `PORTRAIT`, `LANDSCAPE`, `REVERSE_PORTRAIT` or `REVERSE_LANDSCAPE`, or hands it back to the sensor with `AUTO`. |
| `setAnimations` | `serial?`, `enabled` | Turns the three system animation scales on or off. Off makes a QA run stable — a screenshot taken mid-transition otherwise catches a half-drawn screen. |

### Screen

| Tool | Arguments | What it does |
|---|---|---|
| `screenshot` | `serial?`, `scale?`, `saveTo?` | Captures the screen and returns `{serial, width, height, scale, savedTo?}` followed by the capture as an **image block**. `scale` is a factor in `(0, 1]`, defaulting to 1 because a full-size capture is what a caller expects. `saveTo` also writes the PNG to an absolute path on the machine running the host. |

::: warning Coordinates in a scaled screenshot
`tap` takes the device's own screen pixels. A screenshot captured at `scale: 0.5` is half that size,
so a coordinate read off it has to be doubled — or capture at full size and skip the arithmetic.
:::

### Input

| Tool | Arguments | What it does |
|---|---|---|
| `tap` | `serial?`, `x`, `y`, `unit?` | Taps a point. Rejected if it is not on the screen as it is currently rotated, with the size in the message. |
| `longPress` | `serial?`, `x`, `y`, `unit?`, `durationMs?` | Presses and holds. Defaults to 800 ms, which clears every platform long-press timeout (400–500 ms) with margin. |
| `swipe` | `serial?`, `fromX`, `fromY`, `toX`, `toY`, `unit?`, `durationMs?` | Drags between two points; both ends are checked. Defaults to 300 ms, which the platform reads as a drag rather than a fling. |
| `type` | `serial?`, `text` | Types into whatever has focus — tap the field first. Spaces and shell metacharacters are escaped for you. |
| `key` | `serial?`, `key` | Sends a named key, as `KEYCODE_<name>`: `BACK`, `HOME`, `ENTER`, `TAB`, `DEL` (backspace), `ESCAPE`, `APP_SWITCH`, `POWER`, `VOLUME_UP`, `VOLUME_DOWN`, `DPAD_UP`, `DPAD_DOWN`, `DPAD_LEFT`, `DPAD_RIGHT`, `DPAD_CENTER`, `MENU`, `SEARCH`, `CAMERA`, `WAKEUP`, `SLEEP`. |
| `keyCode` | `serial?`, `code` | Sends a raw Android key code, for keys `key` does not name. |

### Apps

| Tool | Arguments | What it does |
|---|---|---|
| `installApk` | `serial?`, `apkPath`, `reinstall?`, `grantPermissions?` | Installs an APK from an absolute path on this machine. Both flags default to `true`, because a QA loop reinstalls the same app and should not stop on a permission dialog. Reports the `INSTALL_FAILED_*` reason, not just an exit code. |
| `uninstallApp` | `serial?`, `packageName`, `keepData?` | Uninstalls an app. |
| `appInfo` | `serial?`, `packageName` | `{installed, versionName, versionCode, firstInstallTime}`. |
| `launchApp` | `serial?`, `packageName`, `activity?` | Launches the app. With no `activity`, the launcher activity is resolved first. The app is **not** force-stopped, so a warm start stays a warm start — call `stopApp` first when you want a cold one. |
| `stopApp` | `serial?`, `packageName` | Force-stops the app. |
| `clearAppData` | `serial?`, `packageName` | Takes the app back to a first-run state. |
| `grantPermission` / `revokePermission` | `serial?`, `packageName`, `permission` | Grants or revokes a runtime permission. |
| `currentActivity` | `serial?` | The foreground activity as `{packageName, activity}` — use it to confirm a launch landed. |
| `openUrl` | `serial?`, `url`, `packageName?` | Opens a URL or deep link with `ACTION_VIEW`. Naming the package skips the chooser dialog. |
| `startActivity` | `serial?`, `action?`, `component?`, `dataUri?`, `extras?` | A generic `am start` builder for what the tools above do not cover. Every value is quoted for the device's shell. |

### Logs

| Tool | Arguments | What it does |
|---|---|---|
| `logcat` | `serial?`, `packageName?`, `tag?`, `priority?`, `lines?`, `since?` | Reads the log and returns it as text. `packageName` selects the app's running process by pid. `lines` defaults to 200, which keeps the payload something an agent can read. `since` takes logcat's own `"MM-DD hh:mm:ss.mmm"`. Never streams — the call always terminates. |
| `clearLogcat` | `serial?` | Empties the buffers, so the next read shows only what happened after this point. |

### Files and ports

| Tool | Arguments | What it does |
|---|---|---|
| `pushFile` | `serial?`, `hostPath`, `devicePath` | Copies a file onto the device — a fixture, a database, a config. |
| `pullFile` | `serial?`, `devicePath`, `hostPath` | Copies a file off the device. |
| `reversePort` | `serial?`, `devicePort`, `hostPort?`, `remove?` | Maps a device port to one on this machine, for when the debug tool listens on a non-default port. `hostPort` is required unless `remove` is `true`. |

## A QA pass, end to end

```text
listDevices                        → serial: emulator-5554
setAnimations   enabled=false      → a screenshot cannot catch a transition
installApk      apkPath=…/app-debug.apk
launchApp       packageName=com.example.qa.sample
currentActivity                    → com.example.qa.sample/.MainActivity
screenshot                         → the screen, as an image
```

From there, the [Compose Semantics Inspector](/guide/compose-semantics-inspector) turns pixels into
structure: its `findNodes` returns each node's `bounds` **and a ready-made `tap` point in screen
pixels**, which is exactly what this plugin's `tap` takes.

```text
semantics.findNodes  text="Sign in" → tap: {x: 540, y: 1180}
tap                  x=540 y=1180
type                 text="a b 'c'"
key                  key=BACK
logcat               packageName=com.example.qa.sample
clearAppData         packageName=com.example.qa.sample  → back to first run
```

Pairing the two is the point: the semantics tree says *where* to tap, and this plugin does the
tapping — no screenshot arithmetic in between.

## Permissions

Every tool here is a plugin tool, so the host's
[MCP permissions](/guide/mcp-server#permissions) apply: tools whose permission is denied are not
listed at all.

These tools change or destroy state on the device. Like every plugin tool they are allowed by
default, so revoke the ones an agent should not run:

| Tool | What it costs |
|---|---|
| `uninstallApp` | The app and, unless `keepData`, everything it stored |
| `clearAppData` | Databases, preferences, caches and granted runtime permissions |
| `installApk` | Replaces the installed build of the same application id |
| `stopApp` | The app's in-memory state |
| `revokePermission` | Can restart the app's process |
| `pushFile` | Overwrites whatever is at `devicePath` |

## Limits

- **`type` is printable ASCII only.** `input text` writes through the key character map, so
  anything else is rejected with a message naming the characters rather than sent as garbage. For
  non-ASCII input, set the text through the app — the Compose Semantics Inspector's
  `performNodeAction` has a `SetText` action.
- **`type` cannot send a literal `%s` in one call**, because `input text` turns every `%s` into a
  space. Such text is rejected; type it up to and including the `%`, then the rest.
- **There is no `shell` tool.** An arbitrary shell reintroduces exactly the class of mistake this
  plugin exists to remove. `startActivity` covers the intent case; anything else is a missing tool,
  not a missing escape hatch.
- **No screen recording**, because an agent cannot read a video, and **no UI automator dump** — the
  [Compose Semantics Inspector](/guide/compose-semantics-inspector) covers the tree, faster and with
  more in it.
- **Android only.** iOS devices are not driven by adb and are out of scope here.
