# Background Work

The Background Work plugin shows the work an app has scheduled to run in the background — and lets
you cancel it or run it now, from the host or from an AI agent over MCP. It answers questions that
are otherwise a `dumpsys` away: is the sync scheduled, what is it waiting for, when does it run
next, why did it stop, and did the last run fail?

## Setup

### Install the host plugin

Background Work is in the host's **official catalog**: open **Settings → Plugins → Add Plugins →
Official Plugins** and install it. See [Host Settings → Plugins](/guide/host-settings#plugins) for the
other install routes.

### Add the agent to your app

```kotlin
dependencies {
    implementation("com.kitakkun.jetwhale:jetwhale-agent-runtime:<version>")
    implementation("com.kitakkun.jetwhale:jetwhale-background-work-agent:<version>")
    // Android apps on WorkManager:
    implementation("com.kitakkun.jetwhale:jetwhale-background-work-agent-workmanager:<version>")
}
```

```kotlin
startJetWhale {
    plugins {
        register(JetWhaleBackgroundWorkAgentPlugin.platformDefaults())
    }
}
```

WorkManager is a separate artifact so an app that does not use it does not depend on it. Add it to
the sources:

```kotlin
JetWhaleBackgroundWorkAgentPlugin(
    sources = { BackgroundWorkSource.platformDefaults() + BackgroundWorkSource.workManager(WorkManager.getInstance(context)) },
)
```

`sources` is called when the host enables the plugin, so a scheduler that needs the app to be up can
be reached lazily. Implement `BackgroundWorkSource` to show a scheduler of your own.

## What each platform shows

| Source | Lists | Cancel | Run now |
|---|---|---|---|
| **WorkManager** (Android, adapter) | Every WorkInfo, live: state, worker class, tags, constraints, next run time, period and flex, run attempts, progress, output, stop reason | By id, by tag, by unique name | Enqueues a one-time copy — see below |
| **JobScheduler** (Android) | The app's pending jobs: service, constraints, period, latency and deadline | By id (not WorkManager's own jobs) | No — the pane shows `adb shell cmd jobscheduler run -f <package> <id>` |
| **Alarm clock** (Android) | The next alarm clock, when the app set it | No | No |
| **Services** (Android) | The app's own running services, foreground or not | Stops the service | — |
| **BGTaskScheduler** (iOS) | Pending `BGAppRefreshTaskRequest` and `BGProcessingTaskRequest`s with their earliest begin date and requirements | By identifier | No — the pane shows the lldb command |
| JVM, macOS, web | Nothing: no system scheduler an app can read | | |

JobScheduler and BGTaskScheduler have no change notifications, so they are read every two seconds.
WorkManager is observed through its own flow.

## What cannot be done from inside the app

- **Running WorkManager work early.** WorkManager has no API to start enqueued work before its
  constraints and delay are met. *Run now* enqueues a one-time copy of the same worker class with the
  same tags, no constraints and no delay, while the original keeps waiting. `WorkInfo` does not
  expose the original's input data, so **the copy runs with empty input**. (`WorkManagerTestInitHelper`
  and its `TestDriver` can force constraints, but only in tests.)
- **Unique names.** `WorkInfo` does not say which unique name work was enqueued under, so the list
  cannot show it. *Cancel unique work…* takes the name you type.
- **Forcing a JobScheduler job or an iOS task.** Only the shell (`adb shell cmd jobscheduler run -f`)
  or the debugger can; the detail pane shows the command to copy. On iOS, pause the app in Xcode and
  run:

  ```
  e -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateLaunchForTaskWithIdentifier:@"<identifier>"]
  ```

  The plugin shows this rather than calling the private selector itself.
- **Ordinary alarms.** Android lets an app read back only the next alarm clock; alarms set with
  `AlarmManager.set*` show up only in `adb shell dumpsys alarm`.

## What you get in the host

- **The list**, filterable by *All / Pending / Finished* and by text across name, id, tag and source.
  Scheduler problems appear as warning chips above it (a source that failed says why).
- **The detail pane** for the selected work: its class and id, schedule and constraints, the reason
  it last stopped, progress and output data, and **the history of states the host saw it in** —
  periodic work cycling between *Enqueued* and *Running*, or a one-off going to *Failed*.
- **Actions**: *Run now*, *Cancel…*, and cancelling everything with one of its tags. Cancelling always
  asks first.

## MCP tools

| Tool | What it does |
|------|--------------|
| `com.kitakkun.jetwhale.background.listBackgroundWork` | Every source and its work, optionally filtered by `state` or `query` |
| `com.kitakkun.jetwhale.background.cancelWork` | Cancel by `id`, `tag` or `uniqueName` (exactly one) in a `source` |
| `com.kitakkun.jetwhale.background.runWorkNow` | Run work now where the source can; the result says what was actually done |

An agent checking that a flow schedules its sync can call `listBackgroundWork` with
`query: "sync"`, run it with `runWorkNow`, and confirm the new run's output in the next listing.
