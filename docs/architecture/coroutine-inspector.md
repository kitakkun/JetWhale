# Coroutine Inspector — design

## Goal

Let a developer or an AI agent find coroutines that are stuck, leaking or blocking the main thread
in a running app, on every platform the agent runs on, without a compiler plugin and without
rewriting the app. The app cooperates in a few lines; everything else uses public
kotlinx.coroutines API.

## What is observed, and how

| Report | Source | App's part |
|--------|--------|------------|
| Coroutine tree | `Job.children`, walked on request from each registered `Job` | `register(scope, name)` |
| Coroutine name, dispatcher | a `launch`/`async` coroutine's `Job` is also its `CoroutineScope`, so its `coroutineContext` gives `CoroutineName` and the `ContinuationInterceptor` | name coroutines with `CoroutineName` |
| Dispatcher load | a `CoroutineDispatcher` wrapper timing each task from `dispatch` to start and from start to end | use the dispatcher `track(...)` returns |
| Flow activity | a `flow { }` wrapper counting collections, outcomes and emissions | use the flow `track(...)` returns |
| Suspension stacks | `kotlinx-coroutines-debug`'s `DebugProbes.dumpCoroutines`, a `compileOnly` dependency on the JVM | add the library and `DebugProbes.install()` |

### Pull, not push

The agent pushes nothing. The host asks for the visible tab once a second while it is shown, so an
app no one is inspecting pays only for what tracking itself costs: a clock read and a few atomic
counter updates per dispatch or emission.

### Bounds

- A tree walk stops at 5,000 coroutines and says it was cut short.
- A dispatcher keeps its 50 most recent long runs; a flow its 20 most recent values, each cut to
  200 characters. The emission rate counts emissions in one-second buckets over the last ten
  seconds, so a flow keeps at most ten buckets however fast it emits, and the rate has no ceiling.

### References

The inspector holds registered jobs, and the jobs its last walk saw, through weak references. A
scope the app drops without cancelling it leaves coroutines that only its `Job` still reaches; if
the inspector held that `Job`, it would keep the whole tree — and whatever its coroutines capture,
such as an Activity — alive, creating the very leak it is there to reveal. On the JVM, Android and
Kotlin/Native such a scope disappears once it is collected. Kotlin/JS and Kotlin/Wasm hold the jobs
strongly until they complete: JavaScript's `WeakRef` takes a Kotlin object directly on Kotlin/JS but
only as a `JsReference` on Kotlin/Wasm, so one shared web implementation cannot use it.

Dispatcher and flow records are kept for the life of the plugin and are keyed by name, so a name is
meant to stand for a purpose rather than an instance.

### Ages

A `Job` records no start time. The walker remembers each `Job` it has seen (and forgets one no walk
finds any more) and reports how long it has been seen. Ids stay stable across walks for the same
reason.

### States

`New`, `Active`, `Cancelling`, `Completed`, `Cancelled` come from `isActive`, `isCompleted` and
`isCancelled`. "Completing" (body finished, children still running) is indistinguishable from
`Active` through public API.

## Findings

- **DebugProbes on Android — verified not possible in-process.** On an API 37 emulator,
  `DebugProbes.install()` from `Application.onCreate` throws `IllegalStateException: Failed to
  access VM name via management factory`, caused by `ClassNotFoundException:
  java.lang.management.ManagementFactory`: ByteBuddy's self-attach needs `java.lang.management`,
  which ART does not have. The library's resources also collide with other dependencies at
  packaging (`META-INF/AL2.0`). The Android agent therefore reports the dump as unavailable.
- **JVM desktop** installs DebugProbes without issue; the demo does so, and the dump shows the
  demo's stuck coroutine suspended in `awaitCancellation`.
- **Dispatchers.Main on the desktop** needs `kotlinx-coroutines-swing`; without it, tracking
  `Dispatchers.Main` fails at the first dispatch like any use of `Dispatchers.Main` would.

## Not done, and why

- **Automatic instrumentation** (naming or tracking every `launch` via the agent compiler plugin)
  — possible, but rewriting call sites breaks across Kotlin versions; worth it only if the few lines
  of registration prove to be a burden.
- **Stacks on Android, iOS and the web** — no DebugProbes; an agent-side replacement would need
  compiler instrumentation.
- **Wrapping `StateFlow`/`SharedFlow` as themselves** — the wrapper is a plain `Flow`; keeping the
  hot-flow type would mean reimplementing their interfaces.
