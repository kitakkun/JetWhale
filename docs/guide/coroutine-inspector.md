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

#### Coroutines your Composables start

`LaunchedEffect`, `produceState`, `collectAsState` and the scopes `rememberCoroutineScope` returns
all run as children of their composition's effect job. Add the Compose adapter and call it once at
the root of each window or `ComposeView`; every Composable below it, lazy list items and other
subcompositions included, then shows under that name:

```kotlin
dependencies {
    implementation("com.kitakkun.jetwhale:jetwhale-coroutine-inspector-agent-compose:<version>")
}
```

```kotlin
@Composable
fun App() {
    inspector.TrackCompositionCoroutines(name = "Compose")
    // ...
}
```

A coroutine leaves the tree when its Composable leaves composition, so navigating away from a screen
shows at once whether its work stopped with it. The root goes away when `TrackCompositionCoroutines`
itself leaves composition.

These coroutines have no name of their own and are listed as `StandaloneCoroutine`. Name the ones
you want to recognize:

```kotlin
LaunchedEffect(userId) {
    withContext(CoroutineName("profile-poll")) {
        while (true) { refresh(userId); delay(5.seconds) }
    }
}

val scope = rememberCoroutineScope()
Button(onClick = { scope.launch(CoroutineName("save-draft")) { save() } }) { Text("Save") }
```

Naming each effect by hand is the price of doing this without a compiler plugin; naming them after
their call site automatically would need one.

#### Dumps with suspension stacks (JVM desktop)

Add `org.jetbrains.kotlinx:kotlinx-coroutines-debug` to a desktop app and call
`DebugProbes.install()` at startup. A selected coroutine's detail then shows whether it is running or
suspended and the stack it waits at, and the **Dump** tab lists every coroutine with its stack. The
agent does not bring the library along itself.

Android cannot do this: `DebugProbes.install()` attaches a JVM agent through ByteBuddy, and ART has
no `java.lang.management` to attach with, so it throws. iOS and the web have no DebugProbes.

## What you get in the host

- **Coroutines** — a tree per registered scope: each coroutine's name, state, how long the inspector
  has seen it and its dispatcher. Filter by state, by age (a coroutine alive for minutes where you
  expected seconds is a leak or a stall), by name and by dispatcher; **Named only** hides coroutines
  without a `CoroutineName`, which is most of what libraries such as Compose start, and the count
  then reads "N of M". **Click a coroutine** for its
  detail: what its state means, where it waits (on the JVM with DebugProbes: running or suspended,
  its stack with the first frame of your own code called out, and where it was created), its path
  from the registered scope, and how many coroutines below it are in each state. The selection
  stays across refreshes; a coroutine that finishes is shown as gone, as it last looked.
- **Dispatchers** — for each tracked dispatcher: tasks waiting and running, average and maximum wait
  before a task starts, average and maximum time a task holds the thread, and its long-run
  threshold. **Long runs** are grouped by the coroutine that held the thread — how often, the
  longest, the total — or listed one by one. **Untracked dispatchers** — `Dispatchers.Default`,
  `Dispatchers.IO` and any other the coroutines in the registered scopes run on without being
  tracked — are listed below with how many coroutines are on each, and a snippet that tracks them.
  They have no times: timing needs every task to go through the tracked wrapper, and these
  dispatchers publish no statistics of their own.
- **Flows** — for each tracked flow: collectors running now, how collections ended (completed,
  cancelled, failed), emissions per second and the most recent values. A cold flow collected twice
  shows every value twice.
- **Dump** — the full dump, on request, with a filter that keeps only the coroutines whose stack
  mentions a text, such as your package or class name.

**Auto-refresh** reads the visible tab from the app once a second while it is shown. Turn it off to
freeze what you see — the toolbar says the view is paused and when it was read; the app itself keeps
running. **Refresh now** reads once either way.

## MCP tools

| Tool | What it does |
|------|--------------|
| `com.kitakkun.jetwhale.coroutines.getCoroutineTree` | The tree, optionally filtered by state, name, dispatcher, minimum age and named-only, with the match count when filtered |
| `com.kitakkun.jetwhale.coroutines.getCoroutineDetail` | One coroutine by id: path, descendants by state, and — with DebugProbes — running or suspended and its stack |
| `com.kitakkun.jetwhale.coroutines.getDispatcherStats` | Queue and run times per tracked dispatcher, with the long runs, and coroutine counts per untracked dispatcher |
| `com.kitakkun.jetwhale.coroutines.getTrackedFlows` | Collectors, outcomes, rate and recent values per tracked flow |
| `com.kitakkun.jetwhale.coroutines.dumpCoroutines` | Every coroutine with its suspension stack, where the app can produce one |

## Limits

- **Only what you register.** Coroutines outside registered scopes, and `GlobalScope`, are not in
  the tree.
- **A registered scope is held weakly**, so the inspector never keeps a tree alive the app has let
  go of. A scope the app registers and then keeps no reference to disappears from the tree once it
  is collected, coroutines and all; keep the scope where the app uses it, as it normally would.
- **Age is observed, not exact.** A `Job` does not record when it started, so the age counts from
  the first time the inspector saw the coroutine: a registered scope from its registration, and a
  coroutine from the first sighting after it started. While the host has the plugin active, the
  agent looks every two seconds, so an age can be up to two seconds short.
- **"Completing" reads as Active.** `Job`'s public API cannot tell a coroutine still running its
  body from one waiting for its children.
- **A tracked dispatcher dispatches every task**, so each one is timed: `Dispatchers.Main.immediate`
  loses its immediate execution, and a dispatcher's own timer for `delay` is not carried over.
  `Dispatchers.Unconfined` cannot be tracked, and each dispatcher needs its own name.
- **A tracked `StateFlow` or `SharedFlow` is a plain `Flow`**, so track it where it is collected.
