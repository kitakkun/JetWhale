# Main Thread Monitor

The Main Thread Monitor finds what blocks the main thread of the app you are debugging: tasks that
run long and where their stacks were while they did, disk and network access that StrictMode catches
on the main thread, and janky frames. It needs no code in the app beyond registering it, and an AI
agent can read everything over MCP — "find the disk writes on the main thread and move them off"
becomes a task it can check its own work on.

## Setup

### Install the host plugin

The Main Thread Monitor is in the host's **official catalog**: open **Settings → Plugins → Add
Plugins → Official Plugins** and install it with one click. See
[Host Settings → Plugins](/guide/host-settings#plugins) for the other install routes.

### Add the agent to your app

```kotlin
dependencies {
    implementation("com.kitakkun.jetwhale:jetwhale-agent-runtime:<version>")
    implementation("com.kitakkun.jetwhale:jetwhale-main-thread-monitor-agent:<version>")
}
```

```kotlin
startJetWhale {
    plugins {
        register(JetWhaleMainThreadAgentPlugin())
    }
}
```

The agent hooks the main thread only while the plugin is enabled in the host, and puts back
whatever the app had in place when it is disabled.

## What each platform reports

| Platform | Long tasks | Stack samples | StrictMode | Frames |
|----------|------------|---------------|------------|--------|
| Android | Every message the main Looper dispatches | Yes | Android 9 (API 28)+ | Android 7 (API 24)+ |
| JVM desktop | Every event the AWT event dispatch thread handles (Compose Desktop's main dispatcher runs there) | Yes | — | — |
| iOS, macOS | — | — | — | — |
| Web | — | — | — | — |

Apple and web targets report that they cannot watch their main thread yet rather than showing an
empty timeline: a run-loop observer or the browser's Long Tasks API could time long work there, but
neither says what the work was.

## What you get in the host

- **Hotspots.** While a main-thread task runs past the long-task threshold (100 ms by default), its
  stack is read every 20 ms. Samples are grouped by the innermost frames that belong to the app —
  platform and library frames are skipped — and ranked by the time they account for. Each hotspot
  shows its latest full stack.
- **Long tasks & frames.** The last minute as a timeline: each long task a bar as tall as it was long,
  red once it reached the unresponsive threshold (5 s by default, the time after which Android
  reports an ANR), and each janky frame a tick below, so a stutter lines up with its cause. Below
  it, every long task with what was dispatched and frame percentiles.
- **StrictMode.** Disk reads and writes, network access, custom slow calls and unbuffered I/O on the
  main thread, grouped by kind and by the app code that caused them, with the latest stack.
- **Thresholds.** The long-task threshold, sample interval and unresponsive threshold, changed live.

**Clear** starts a fresh recording, so an interaction can be measured on its own.

### StrictMode and apps with their own policy

StrictMode is collected by extending the main thread's policy. Every Android app starts with the
platform's policy, which makes network access on the main thread throw; the monitor builds on it,
so that still happens as before (and a network call that throws is not reported as a violation,
because Android throws before it reports).

An app that sets its **own** thread policy keeps it untouched: adding detections to it would apply
its penalties — `penaltyDeath` included — to violations it never asked about. The host then says
that StrictMode is not being collected; the app's own penalties (such as `penaltyLog`) still report
its violations as before.

## MCP tools

| Tool | What it does |
|------|--------------|
| `com.kitakkun.jetwhale.mainthread.getHotspots` | Where the main thread was stuck, most blocking first, with stacks |
| `com.kitakkun.jetwhale.mainthread.getViolations` | StrictMode violations by kind and call site (`kind` narrows it) |
| `com.kitakkun.jetwhale.mainthread.getLongTasks` | Long tasks, latest last (`minDurationMillis` narrows it) |
| `com.kitakkun.jetwhale.mainthread.getFrameStats` | Frame counts, percentiles and the latest janky frames |
| `com.kitakkun.jetwhale.mainthread.resetStats` | Clears everything recorded |

Every result carries what the platform can report and the thresholds in force, so a caller can tell
"nothing blocked the main thread" from "nothing was watched". A typical agent loop is `resetStats`
→ drive the app → `getViolations` / `getHotspots` → fix → repeat.

## Relation to the Coroutine Inspector

This plugin sees the main thread from the platform's side: which message or event ran long and
which frames it was in. A coroutine resumed on the main thread shows up here as the dispatcher's
message, with the coroutine's own frames in the stack. Naming the coroutine itself — and measuring
dispatcher queue latency — is the Coroutine Inspector's job; the two are meant to be used together.
An app can also add its own name for the running work to each long task by setting
`JetWhaleMainThreadAgentPlugin.labelProvider`.
