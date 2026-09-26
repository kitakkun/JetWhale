# Coroutine auto-instrumentation — design

Status: proposed. Builds on the Coroutine Inspector ([design](coroutine-inspector.md)), whose manual
API stays the foundation.

## Goal

Make the Coroutine Inspector useful with no registration code. Every coroutine an app launches
should show up with the place that launched it, and every dispatcher and flow it builds should be
measured, on every platform the agent runs on.

Today the app registers scopes (`register`) and wraps dispatchers and flows (`track`) by hand.
Forgetting one hides it. `LaunchedEffect` and other unnamed coroutines show as
`StandaloneCoroutine`, with nothing that says which code started them.

## What a compiler plugin can do that the runtime cannot

| Need | Runtime-only answer | With a compiler plugin |
|------|---------------------|------------------------|
| Find every coroutine | only below registered `Job`s; DebugProbes on the JVM only | every `launch`/`async` in instrumented code reports its `Job` |
| Where it was launched | creation stack traces, JVM only, when enabled | `file:line` of the call site, on every platform |
| Dispatcher load | only for dispatchers the app wraps | reads of `Dispatchers.Default`/`IO` return a measuring wrapper |
| Flow activity | only for flows the app wraps | `flow { }`, `stateIn`, `shareIn` wrapped at the call site |

## Shape: fixed hooks, and a slot the inspector fills

Instrumented code cannot be handed the inspector instance. A `launch` call sits in an arbitrary
function with no access to the app's DI graph or to `startJetWhale`. So the compiler plugin emits
calls to **static hook functions** in a small runtime artifact. The inspector **installs itself** into
that artifact's slot when it activates, and removes itself when it deactivates:

```kotlin
// jetwhale-plugins/coroutines/hooks (KMP, depends on kotlinx-coroutines-core only)
public object JetWhaleCoroutineHooks {
    public fun install(sink: CoroutineHookSink?)

    public fun onLaunched(job: Job, callSite: String)
    public fun dispatcher(dispatcher: CoroutineDispatcher, name: String): CoroutineDispatcher
    public fun <T> flow(flow: Flow<T>, callSite: String): Flow<T>
}

public interface CoroutineHookSink {
    public fun onLaunched(job: Job, callSite: String)
    public fun dispatcher(dispatcher: CoroutineDispatcher, name: String): CoroutineDispatcher
    public fun <T> flow(flow: Flow<T>, callSite: String): Flow<T>
}
```

- **Empty slot** (JetWhale not started, the plugin disabled, or a build that never runs JetWhale):
  each hook reads one atomic reference and returns its input unchanged.
- **Filled slot**: the inspector routes the hooks to what exists today. `onLaunched` becomes a
  registration held weakly (see [References](coroutine-inspector.md#references)); `dispatcher` and
  `flow` become the `track` wrappers, cached per name so repeated reads return one wrapper.
- **The inspector depends on the hooks artifact, not the reverse.** The hooks artifact knows nothing
  of messaging or the host. An app can ship the instrumented code without the inspector plugin
  installed.

### What gets rewritten

| Source | Emitted |
|--------|---------|
| `scope.launch(ctx) { … }` / `async` | `scope.launch(ctx + CoroutineName("HomeViewModel.kt:42")) { … }.also { JetWhaleCoroutineHooks.onLaunched(it, "HomeViewModel.kt:42") }`; an explicit `CoroutineName` in `ctx` wins over the call site |
| `Dispatchers.Default`, `Dispatchers.IO` (property reads) | `JetWhaleCoroutineHooks.dispatcher(Dispatchers.IO, "IO")` |
| `flow { … }`, `x.stateIn(…)`, `x.shareIn(…)` | `JetWhaleCoroutineHooks.flow(<original>, "Repo.kt:18")`; for `stateIn`/`shareIn`, the hot flow's upstream is wrapped, so the result keeps its `StateFlow`/`SharedFlow` type |

The call-site string is `<file name>:<line>`, plus the enclosing function's name when there is one
(`HomeViewModel.load (HomeViewModel.kt:42)`). It is computed at compile time and costs a constant
string.

### Deliberately not rewritten

- **`Dispatchers.Main` and `Main.immediate`.** A plain wrapping dispatcher loses `immediate`'s
  "run in place when already on Main" behavior (the manual `track` documents this). Main stays
  opt-in through `track`, or a later wrapper that keeps `isDispatchNeeded`.
- **Code in libraries.** Only modules compiled with the plugin are rewritten. Compose's own
  `LaunchedEffect` machinery is covered by `TrackCompositionCoroutines` (#302), not by rewriting
  Compose.
- **`GlobalScope.launch`.** It is rewritten like any other launch site. There is no special root
  for it; the inspector lists such coroutines under a synthetic "Unscoped" root.
- **`runBlocking`, `coroutineScope`, `withContext`.** These start no independent coroutine that
  outlives the caller, so there is nothing to register. They already appear in the tree below
  their parent.

## Module layout

| Piece | Location | Why there |
|-------|----------|-----------|
| Compiler plugin | `jetwhale-agent-plugin/jetwhale-coroutines-compiler-plugin` | Same included build as the existing agent compiler plugin: same `kotlin-compiler-embeddable` pinning, the same `-Pkotlin.compiler` / `-Pkotlin.plugin.api` sweep, and the same box-test setup |
| Shared compat layer | `jetwhale-agent-plugin/jetwhale-compiler-plugin-common` (internal, not published separately; shaded or included into both plugin JARs) | `CompilerApiCompat` moves here so a Kotlin bump is fixed once for both plugins |
| Hooks runtime | `jetwhale-plugins/coroutines/hooks` (KMP) | Lives with the Coroutine Inspector's other modules |
| Gradle wiring | `jetwhale-agent-gradle-plugin`: a `coroutines { autoInstrument = true }` block that adds the second compiler plugin and the hooks dependency | One Gradle plugin for users; the second compiler plugin is only on the classpath when asked for |

**Why a second compiler plugin rather than a new transform in the existing one:**

- The existing plugin embeds the build machine's address. It is always on, in every app that uses
  JetWhale. Coroutine rewriting is opt-in. A bug in it must not be able to break every app's
  compile.
- The emitted code calls the hooks runtime, which pulls in kotlinx.coroutines. Only apps that use
  the Coroutine Inspector should carry it.
- Rewriting every launch site and dispatcher read costs compile time. Apps that don't use the
  inspector shouldn't pay it.
- The transform is far more involved than the address rewrite and needs its own box tests and IR
  dump fixtures.

## Debug-only, and where that cannot hold

The existing subplugin applies to every compilation (`isApplicable = true`). The coroutine subplugin
must not:

- **Android**: apply only to compilations of debuggable variants. Release APKs contain no rewritten
  calls and no hooks dependency.
- **JVM desktop**: there are no build types. Apply when `autoInstrument` is set, and document that
  it is meant for development builds (a separate source set or a Gradle property is the user's
  switch).
- **Kotlin/Native (iOS, macOS)**: **the klib compilation is shared by the debug and release
  binaries.** A rewrite cannot be limited to the debug framework without compiling twice. Options,
  to decide before implementation:
  1. Accept it. Release binaries carry the hook calls. With an empty slot, each costs one atomic
     read. The hooks artifact ships in the release framework.
  2. Compile a second, instrumented klib for debug frameworks only (doubles native compile time
     when enabled).
  3. Gate on a Gradle property the user sets for development builds, as on the JVM.

  Recommendation: 3 by default, which is explicit and costs nothing when off, with 1 documented
  as what happens if a release is built with the property on.
- **JS/Wasm**: as JVM desktop.

## Versions

- Same supported range as the existing plugin (`SupportedKotlinVersions`: 2.3 to 2.4 today), checked
  up front with the same message. The CI matrix (`agent-compiler-plugin-matrix.yml`) gains the second
  plugin.
- The hooks runtime and the compiler plugin are released together. The compiler plugin passes the
  hooks ABI version it was built for as a plugin option; the Gradle plugin fails the build on a
  mismatch instead of leaving a linkage error for runtime.
- kotlinx.coroutines: the emitted calls use only `Job`, `CoroutineDispatcher`, `Flow`,
  `CoroutineName` and `CoroutineContext.plus`, all long-stable public API.

## Testing

- Box tests per rewrite (launch/async with and without an explicit name, dispatcher reads,
  `flow`/`stateIn`/`shareIn`), asserting both the emitted IR shape and the runtime behavior with a
  recording sink.
- A negative test per "not rewritten" case (`Main.immediate`, `withContext`).
- The sample project compiles with every Kotlin version in the matrix, with the plugin on and off.
- Hands-on: the demo with `autoInstrument = true` and no `register`/`track` calls. It should show
  the same scenarios as the manual demo, now with call-site names.

## Open questions

- Should `onLaunched` also carry the enclosing class instance's identity (for "which ViewModel
  instance started this"), at the cost of holding it? Probably not: it conflicts with the weak
  reference policy.
- Per-module opt-out (`@JetWhaleNoInstrument` on a file or class) for hot paths.
- Whether `Dispatchers.Default` wrapping should be on by default. Measuring every dispatch adds a
  clock read and a few atomics per task, which is fine for development, but worth a switch.
