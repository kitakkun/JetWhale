# Coroutine Inspector

The Coroutine Inspector shows what the app's coroutines are doing while it runs: the coroutines
alive under the scopes you point it at, how long tasks wait for and hold the dispatchers you track
— a long hold on the main dispatcher is a frozen frame — and what your tracked flows emit. On the
desktop JVM it can also dump every coroutine with the stack it is suspended at. An AI agent can
read all of it over MCP to find a stuck or leaking coroutine.

Nothing is instrumented behind your back and no compiler plugin is involved: you register what you
want to see, in a few lines.

## Setup

### Install the host plugin

The Coroutine Inspector is in the host's **official catalog**: **Settings → Plugins → Add Plugins
→ Official Plugins**.

### Add the agent to your app

```kotlin
dependencies {
    implementation("com.kitakkun.jetwhale:jetwhale-agent-runtime:<version>")
    implementation("com.kitakkun.jetwhale:jetwhale-coroutine-inspector-agent:<version>")
}
```

```kotlin
val inspector = JetWhaleCoroutineInspectorAgentPlugin()

startJetWhale { plugins { register(inspector) } }
```

Then point it at what you want to see:

```kotlin
// Coroutines: every scope you register shows as a root of the tree.
inspector.register(applicationScope, name = "Application")

// Dispatchers: use the returned dispatcher where you used the original.
val main = inspector.track(Dispatchers.Main, name = "Main", longRunThreshold = 16.milliseconds)

// Flows: track a flow where it is collected.
val prices = inspector.track(repository.prices, name = "prices")
```

Name your coroutines with `CoroutineName` — the tree and the long-run list show that name.

#### Dumps with suspension stacks (JVM desktop)

Add `org.jetbrains.kotlinx:kotlinx-coroutines-debug` to a desktop app and call
`DebugProbes.install()` at startup; the **Dump** tab then lists every coroutine with the stack it is
suspended at. The agent does not bring the library along itself.

Android cannot do this: `DebugProbes.install()` attaches a JVM agent through ByteBuddy, and ART has
no `java.lang.management` to attach with, so it throws. iOS and the web have no DebugProbes.

## What you get in the host

- **Coroutines** — a tree per registered scope: each coroutine's name, dispatcher, state and how
  long the inspector has seen it. Filter by state, by age (a coroutine alive for minutes where you
  expected seconds is a leak or a stall), by name and by dispatcher.
- **Dispatchers** — for each tracked dispatcher: tasks queued and running, average and maximum wait
  before a task starts, average and maximum time a task holds the thread, and the recent **long
  runs** with the coroutine that ran.
- **Flows** — for each tracked flow: collectors running now, how collections ended (completed,
  cancelled, failed), emissions per second and the most recent values.
- **Dump** — the full dump, on request.

The visible tab refreshes once a second while it is shown; **Pause** freezes it.

## MCP tools

| Tool | What it does |
|------|--------------|
| `com.kitakkun.jetwhale.coroutines.getCoroutineTree` | The tree, optionally filtered by state, name, dispatcher and minimum age |
| `com.kitakkun.jetwhale.coroutines.getDispatcherStats` | Queue and run times per tracked dispatcher, with the long runs |
| `com.kitakkun.jetwhale.coroutines.getTrackedFlows` | Collectors, outcomes, rate and recent values per tracked flow |
| `com.kitakkun.jetwhale.coroutines.dumpCoroutines` | Every coroutine with its suspension stack, where the app can produce one |

## Limits

- **Only what you register.** Coroutines outside registered scopes, and `GlobalScope`, are not in
  the tree.
- **Age is observed, not exact.** A `Job` does not record when it started, so the age counts from
  the first time the inspector saw the coroutine.
- **"Completing" reads as Active.** `Job`'s public API cannot tell a coroutine still running its
  body from one waiting for its children.
- **A tracked dispatcher is a plain dispatcher.** `Dispatchers.Main.immediate`'s immediate
  execution and a dispatcher's own timer for `delay` are not carried over.
- **A tracked `StateFlow` or `SharedFlow` is a plain `Flow`**, so track it where it is collected.
