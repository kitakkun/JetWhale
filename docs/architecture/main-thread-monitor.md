# Main Thread Monitor — design

What blocks the main thread, found from inside the app without code in it: which task ran long,
where its stack was while it did, the disk and network access StrictMode sees on the main thread,
and the frames that missed their deadline.

## Shape

- `jetwhale-plugins/mainthread/protocol` — the report (long tasks, hotspots, violation groups,
  frame stats, capabilities, settings) and three requests: get the report, update settings, reset.
- `…/agent` — `MainThreadRecorder` (common, bounded, thread-safe) fed by a per-platform
  `MainThreadProbe`. The host pulls the report; the agent pushes nothing, so an idle host costs the
  app nothing beyond the probes themselves.
- `…/host` — polls the report once a second while its screen is shown, and exposes the same data as
  MCP tools.

Nothing runs until the plugin is activated; deactivation stops the sampler and puts back every hook
it replaced.

## Probes

### Android

| Signal | Hook | Restored on stop |
|--------|------|------------------|
| Task timing | `Looper.getMainLooper().setMessageLogging` — the `>>>>> Dispatching` / `<<<<< Finished` lines bracket each message | The app's own printer, read reflectively from `Looper.mLogging` and chained to while we run |
| Stack samples | A daemon thread reads `Looper.getMainLooper().thread.stackTrace` while a task runs past the threshold | Thread stopped |
| StrictMode | `ThreadPolicy.Builder(previous)` + detections + `penaltyListener` (API 28+), set on the main thread | The previous policy |
| Frames | `Window.addOnFrameMetricsAvailableListener` per resumed activity (API 24+), via `ActivityLifecycleCallbacks` and, for activities already resumed, ActivityThread's records | Listeners and callbacks removed |

The printer is called for every message, so it does a prefix check and hands the raw line to the
recorder; the line is only parsed into a label if the task turns out to be long.

The platform's default thread policy is not `LAX`: ActivityThread enables death-on-network for every
app. That policy is recognized (by its string form — policies have no `equals`) and extended; any
other policy belongs to the app and is left alone, because extending it would apply its penalties to
violations it never asked for.

### JVM desktop

A `TimingEventQueue` pushed onto the system event queue brackets each `dispatchEvent` on the event
dispatch thread (Compose Desktop's `Dispatchers.Main` posts there as `InvocationEvent`s); nested
dispatch from a modal loop counts as part of the outer event. `pop()` puts the original queue back.
Stack samples come from the same sampler as on Android.

### Apple, web

Report "unsupported" with a reason. A `CFRunLoopObserver` or the browser's `PerformanceObserver`
for `longtask` could time long work, but neither can say what ran — Kotlin/Native has no API to read
another thread's stack, and Long Tasks carry no stack — so durations alone were not worth a timeline
in this first version.

## Recorder

- A task is recorded only if it reaches `longTaskThresholdMillis`.
- The sampler ticks every `sampleInterval / 2` (at least 5 ms) and asks the recorder whether a sample
  is due: a task running past the threshold, at most once per interval. Reading the stack happens
  outside the lock, and the sample is dropped if the task changed meanwhile.
- Hotspot signature: the three innermost frames that are not from the platform or common libraries
  (`android.`, `java.`, `kotlin.`, `kotlinx.`, `okhttp3.`, …); a stack with none is named by its
  innermost frames. Blocked time is samples × interval — an estimate, not a measurement.
- Bounds: 200 long tasks, 100 hotspots (the least-sampled one is evicted, never the one just
  sampled), 100 violation groups, the last 1000 frame durations and 100 janky frames.

## Overhead

Measured by reasoning and unit tests, not profiled on a device yet:

- Per main-thread message on Android: one prefix check, a lock, two clock reads.
- While no task is long: the sampler wakes every 10 ms (default) for a lock and a comparison.
- While a task is long: one `Thread.getStackTrace()` of the main thread per 20 ms — a safepoint for
  the main thread, typically tens to hundreds of microseconds.
- StrictMode detections add the platform's own bookkeeping to disk and network calls on the main
  thread, which is what they are for.

## Relation to the Coroutine Inspector

Complementary, not overlapping: this plugin attributes stalls to platform messages and stack frames;
the Coroutine Inspector names coroutines and measures dispatcher latency. The seam between them is
`JetWhaleMainThreadAgentPlugin.labelProvider`: whatever it returns when a long task starts is added
to the task's label, so a dispatcher wrapper can name the coroutine it is resuming without this
module depending on it.

## Not done yet

- iOS and web timing (durations only).
- An ANR-style watchdog independent of the Looper printer: a task that never finishes is only
  reported once it does, although its samples accumulate in hotspots while it runs.
- Overhead measured on a physical device.
