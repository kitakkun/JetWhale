# Excluding JetWhale from Release Builds

JetWhale is a debugging tool: it opens a WebSocket to your machine, records HTTP traffic, and lets
an external process drive your app. None of that belongs in a build you ship.

Keeping the *artifact* out is easy — `debugImplementation` on Android, or a build-flavor-specific
dependency elsewhere. Keeping the *call sites* out is the hard part: as soon as your shared code
says `startJetWhale { }` or `networkAgent.ktorClientPlugin()`, the release compilation needs those
symbols too, and the dependency comes right back.

This page describes one way out for apps that use a DI container with contribution merging. The
examples use [Metro](https://zacsweers.github.io/metro/), whose `@ContributesBinding(replaces = …)`
expresses the idea directly, but the shape applies to any DI framework that can swap an
implementation per build variant.

## The problem, concretely

| Approach | Why it falls short |
|----------|--------------------|
| `debugImplementation` alone | The release variant fails to compile the moment shared code references JetWhale. |
| Android `src/debug/kotlin` | Variant source sets are an Android Gradle Plugin feature. A KMP `commonMain` — where your HTTP client and app startup usually live — has no equivalent. |
| `if (BuildConfig.DEBUG) { … }` | Compiles, but the dependency stays on the release classpath, and nothing makes every reference sit behind the check. R8 can drop guarded code from a minified build; anything that slips past the guard ships. |

What all three lack is a **seam**: a boundary that production code can compile against without
knowing JetWhale exists.

## The shape of the fix

Own the abstraction yourself. Production code depends on a small interface of your own; JetWhale
lives behind an implementation of it that only exists on the debug classpath.

```
:app                    @DependencyGraph(AppScope::class)
 │                       implementation(:core:debug)
 │                       debugImplementation(:debug-jetwhale)
 │
 ├── :core:debug        ← always compiled
 │     fun interface DebugToolingInitializer
 │     NoOpInitializer      @ContributesBinding(AppScope::class)
 │
 └── :debug-jetwhale    ← debug classpath only; the only module that imports JetWhale
       JetWhaleInitializer  @ContributesBinding(
                                AppScope::class,
                                replaces = [NoOpInitializer::class],
                            )
```

Both modules contribute a binding for `DebugToolingInitializer`. On the debug classpath the
JetWhale one `replaces` the no-op, so exactly one survives the merge. On the release classpath the
JetWhale module is simply absent and the no-op stands unopposed — no flags, no `expect`/`actual`,
no source set gymnastics.

::: tip Where the switch happens
Metro merges contributions where the `@DependencyGraph` is declared, by scanning that compilation's
classpath. So the variant-specific dependency belongs on the module holding the graph — usually
your app module.
:::

## 1. Declare the seam in production code

```kotlin
// :core:debug — on every classpath, release included
package com.example.debug

fun interface DebugToolingInitializer {
    fun initialize()
}

@ContributesBinding(AppScope::class)
@Inject
class NoOpInitializer : DebugToolingInitializer {
    override fun initialize() = Unit
}
```

That is the whole production-side surface. Nothing here knows what a debugger is — name the seam
after the role it plays in your app, not after the tool behind it.

## 2. Contribute the JetWhale-backed implementation

Everything below lives in `:debug-jetwhale`, the one module that may import
`com.kitakkun.jetwhale.*`.

The Network Inspector requires the *same* `JetWhaleNetworkAgentPlugin` instance in two places — the
HTTP client and `startJetWhale { }` (see [Network Inspector](/guide/network-inspector#setup)). A
scoped binding is exactly the tool for that, so give the agents a single owner:

```kotlin
// :debug-jetwhale
package com.example.debug.jetwhale

@SingleIn(AppScope::class)
@Inject
class JetWhaleAgents {
    val network: JetWhaleNetworkAgentPlugin = JetWhaleNetworkAgentPlugin()
}
```

Then implement the seam:

```kotlin
@ContributesBinding(AppScope::class, replaces = [NoOpInitializer::class])
@Inject
class JetWhaleInitializer(
    private val agents: JetWhaleAgents,
) : DebugToolingInitializer {
    override fun initialize() {
        startJetWhale {
            connection {
                endpoints { ws("localhost", 5080) }
            }
            plugins {
                register(agents.network)
            }
        }
    }
}
```

::: warning `replaces` names the contributing class
`replaces = [NoOpInitializer::class]` — the *class that contributes* the binding, not the bound type
`DebugToolingInitializer`. Both contributions must also target the same scope, or the merge leaves
two bindings in place.
:::

## 3. Start it

Your app calls the seam, never JetWhale:

```kotlin
// :app — Application.onCreate(), or the first line of main()
appGraph.debugToolingInitializer.initialize()
```

In a release build this is a call to an empty method, which R8 removes outright from a minified
Android build.

## 4. Attach the Network Inspector to your HTTP client

Startup is a single binding, so `replaces` fits. Client customization is different: there may be
zero of them, or several. That is a multibinding, and an absent contribution just means an absent
element — no `replaces` needed at all.

Declare the seam and the empty case in production code:

```kotlin
// :core:debug
fun interface HttpClientDecorator {
    fun decorate(client: HttpClient)
}

@ContributesTo(AppScope::class)
interface HttpClientDecoratorDeclarations {
    // Release builds contribute nothing, and an empty multibinding is an error by default.
    @Multibinds(allowEmpty = true)
    fun httpClientDecorators(): Set<HttpClientDecorator>
}
```

Apply them wherever you build the client — still production code, still JetWhale-free:

```kotlin
// :core:network
@ContributesTo(AppScope::class)
interface NetworkBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun provideHttpClient(decorators: Set<HttpClientDecorator>): HttpClient =
        HttpClient().also { client -> decorators.forEach { it.decorate(client) } }
}
```

And contribute the JetWhale decorator from the debug module:

```kotlin
// :debug-jetwhale
@ContributesIntoSet(AppScope::class)
@Inject
class JetWhaleHttpClientDecorator(
    private val agents: JetWhaleAgents,
) : HttpClientDecorator {
    override fun decorate(client: HttpClient) {
        client.plugin(HttpSend).intercept(agents.network.ktorSendInterceptor(client))
    }
}
```

This uses the `HttpSend` interceptor rather than `install(...)` because the client comes from the
graph already built. Register it once per client — `HttpSend` accepts duplicate interceptors
silently and would record every transaction twice.

OkHttp works the same way: contribute an `Interceptor` with `@ContributesIntoSet` from the debug
module, and have the production `OkHttpClient` provider add every element of the (possibly empty)
set.

## 5. Wire the variants in Gradle

### Android

```kotlin
// :app/build.gradle.kts
dependencies {
    implementation(projects.core.debug)
    debugImplementation(projects.debugJetwhale)
}
```

### Kotlin Multiplatform

KMP has no build variants to hang the dependency on. The shape that keeps a release clean however
the build is invoked is two thin entry-point modules — `:app-debug` and `:app-release` — each
declaring its own graph and depending on the appropriate set of modules.

The lighter alternative gates the dependency on a Gradle property:

```kotlin
// :app/build.gradle.kts
val jetwhaleEnabled = providers.gradleProperty("jetwhale.enabled").orNull.toBoolean()

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.debug)
            if (jetwhaleEnabled) implementation(projects.debugJetwhale)
        }
    }
}
```

A property switches the whole invocation, not a build type: while it is set, every compilation in
that run gets `:debug-jetwhale`, release and distribution binaries included. So pass
`-Pjetwhale.enabled=true` on debug runs only, never from a `gradle.properties` file, and build
releases in runs that leave it out. Flipping the property changes the compile classpath, so the
graph module recompiles — expected, and cheap enough for a switch you throw once per session.

## Verify it

The point of all this is a release binary with no trace of JetWhale, so check the classpath rather
than trusting the wiring:

```shell
# Android
./gradlew :app:dependencies --configuration releaseCompileClasspath | grep jetwhale
./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep jetwhale
```

No output from either means no JetWhale — nothing in release can compile against it, so no stray
`startJetWhale` call can reach production, and there are no classes for R8 to process and no keep
rules to write. The compile classpath is the one that proves the first part: a `compileOnly`
dependency would let a call compile and still be missing from the runtime classpath.

## Pitfalls

- **The no-op must live in an always-present module.** If it sits next to the JetWhale
  implementation, release builds lose both and the graph fails to resolve
  `DebugToolingInitializer`.
- **Forgetting `replaces` fails loudly.** The debug build stops at compile time with
  `Metro/DuplicateBinding`, naming both contributions. That is the safety net working — it cannot
  silently pick one.
- **Keep the seam interface out of the debug module.** Production code has to compile against it.
- **Don't let JetWhale types leak into the seam.** The moment `DebugToolingInitializer` mentions
  `JetWhaleSession` or `JetWhaleNetworkAgentPlugin` in its signature, production code needs the
  dependency again.
- **One agent instance, shared.** `@SingleIn(AppScope::class)` on `JetWhaleAgents` is what
  guarantees the plugin registered with `startJetWhale { }` is the one installed into the HTTP
  client. Two instances means the host shows no traffic.

## Other DI frameworks

The seam is the same everywhere; only the mechanism for making the debug side win differs. Each DI
shape below was checked by building and running it with stand-in classes in place of JetWhale's,
not inferred from the annotations:

- **kotlin-inject-anvil** — `@ContributesBinding(replaces = [...])` carries the same meaning and the
  module layout transfers unchanged. kotlin-inject has no equivalent of
  `@Multibinds(allowEmpty = true)`, so model the HTTP decorator as an ordinary binding with a no-op
  default rather than a set.
- **Square Anvil** — runs only on the K1 compiler, which holds a project at Kotlin 2.2, below
  JetWhale's 2.3 floor. Such a project has to move to Metro, or a KSP-based Anvil fork, before it
  can add JetWhale.
- **Hilt** — `@InstallIn` modules are discovered from the classpath, so a `@BindsOptionalOf`
  declaration in `src/main` plus a module in `src/debug` is enough. No release-side counterpart is
  needed; the generated component simply binds `Optional.empty()`.
- **Plain Dagger** — `@Component(modules = [...])` names its modules, and `src/main` cannot name a
  class that only exists in `src/debug`. Here you do need the same fully-qualified module in both
  source sets, empty in release. Note that Dagger's `@Multibinds` allows an empty set by default —
  there is no `allowEmpty` to pass.
- **Koin / manual DI** — resolution is at runtime, so nothing fails the release build for you.
  Declare the same `debugToolingModule` in both source sets, a no-op in release, and give the HTTP
  client `getAll<HttpClientDecorator>()`, which is empty when release declares no decorator.
