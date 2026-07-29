# Wiring with kotlin-inject-anvil, or an existing Square Anvil setup

Two unrelated libraries that happen to share a name and the word `replaces`. Both merge
contributions across the compile classpath, so the module layout and the seam are identical to
Metro's — read [`metro.md`](metro.md) first and treat this file as the delta.

- **kotlin-inject-anvil** (Amazon, actively developed) — the section below.
- **Square Anvil** — in maintenance mode and pinned to an old Kotlin, so that section is written for
  a project that is already on it, and leads with the constraint to check rather than the syntax.

Both were verified by building a four-module project (`:seam`, `:tooling`, `:app-debug` depending on
both, `:app-release` depending on `:seam` only) and running each app: release resolves the no-op,
debug resolves the real implementation, and a scoped holder is shared across two injection sites.
Each has one sharp edge that the Metro shape does not have.

## kotlin-inject-anvil

Verified with kotlin-inject-anvil 0.1.7, kotlin-inject 0.9.0, KSP 2.3.10, Kotlin 2.3.10.

Annotations live under `software.amazon.lastmile.kotlin.inject.anvil`, with kotlin-inject's `@Inject`
(`me.tatarka.inject.annotations.Inject`) and `@SingleIn(AppScope::class)` for scoping. The component
is `@MergeComponent(AppScope::class)` on an abstract class, instantiated with
`AppComponent::class.create()`.

```kotlin
// production module
interface DebugToolingInitializer {
    fun initialize(): String
}

@ContributesBinding(AppScope::class)
@Inject
class NoOpInitializer : DebugToolingInitializer {
    override fun initialize() = "noop"
}
```

```kotlin
// debug-only module
@ContributesBinding(AppScope::class, replaces = [NoOpInitializer::class])
@Inject
@SingleIn(AppScope::class)
class JetWhaleInitializer(
    private val agents: JetWhaleAgents,
) : DebugToolingInitializer {
    override fun initialize() = "jetwhale"
}
```

### Use `replaces` for the HTTP decorator too — not a multibinding

`@ContributesBinding` does carry a `multibinding: Boolean = false` parameter, so the Metro shape
looks like it should transfer. **It does not.** kotlin-inject has no equivalent of
`@Multibinds(allowEmpty = true)`, so a `Set<T>` with zero contributions does not resolve, and the
release build fails at KSP time:

```
e: [ksp] Cannot find an @Inject constructor or provider for: Set<seam.HttpClientDecorator>
```

Release contributing nothing is exactly the case this seam needs, so the multibinding route is
closed. Model the decorator as an ordinary binding with a no-op default and displace it the same way
as the initializer:

```kotlin
// production
@ContributesBinding(AppScope::class)
@Inject
class NoOpDecorator : HttpClientDecorator {
    override fun decorate(client: HttpClient) = Unit
}

// debug-only
@ContributesBinding(AppScope::class, replaces = [NoOpDecorator::class])
@Inject
class JetWhaleHttpClientDecorator(private val agents: JetWhaleAgents) : HttpClientDecorator {
    override fun decorate(client: HttpClient) {
        client.plugin(HttpSend).intercept(agents.network.ktorSendInterceptor(client))
    }
}
```

One mechanism for both seams, and it is the mechanism that works.

## Square Anvil — for a project already on it

Anvil 2.7.0 (October 2025) is built against Kotlin 2.2.20, and Metro is its successor, so this is
not a library a project adopts today. It is one a project already has — often a large, long-lived
Android app, which is exactly the kind that most benefits from keeping the debugger out of its
release build.

### Check the Dagger processor first: kapt, not KSP

Worth settling before writing any wiring. Anvil is a Kotlin **compiler plugin**: it adds
`@Component` and the merged supertypes during compilation. KSP reads source before that happens, so
Dagger's KSP processor never sees a component to process.

There is no warning for this — nothing is generated, and the build fails later at a point that looks
unrelated:

```
e: Unresolved reference: DaggerAppComponent
```

Both variants build and resolve correctly once Dagger's processor moves to kapt:

```kotlin
plugins {
    kotlin("kapt")
}
dependencies {
    kapt("com.google.dagger:dagger-compiler:2.60.1")
}
```

A project that already runs Dagger through KSP cannot have both, so this is a decision for the team
rather than a detail to change in passing.

### The wiring itself

Identical to Metro's, with `com.squareup.anvil.annotations.ContributesBinding`, Dagger's
`@Inject constructor()` and `@Singleton`, and `@MergeComponent(AppScope::class)` on the component —
instantiated as `DaggerAppComponent.create()`. `replaces` still names the contributing class:

```kotlin
// production module
@ContributesBinding(AppScope::class)
class NoOpInitializer @Inject constructor() : DebugToolingInitializer { … }

// debug-only module
@ContributesBinding(AppScope::class, replaces = [NoOpInitializer::class])
class JetWhaleInitializer @Inject constructor(private val agents: JetWhaleAgents) :
    DebugToolingInitializer { … }
```

Verified with Anvil 2.7.0, Dagger 2.60.1, Kotlin 2.2.20.

### The exit

This wiring transfers to Metro with only the annotation packages changed — and there
`@Multibinds(allowEmpty = true)` is available again, so the HTTP decorator can go back to being a
multibinding. If the team is weighing a migration, the seam is a small, self-contained place to
start.

## Common failure modes

| Symptom | Cause |
|---|---|
| Duplicate binding for the seam type | `replaces` missing, or the contributions target different scopes |
| Release cannot resolve the seam | The no-op is in the debug-only module |
| `Cannot find an @Inject constructor or provider for: Set<…>` | kotlin-inject-anvil: an empty multibinding. Use `replaces` with a no-op instead |
| `Unresolved reference: DaggerAppComponent`, nothing generated | Square Anvil with Dagger on KSP. Move Dagger to kapt |
| Contribution silently ignored | The debug module is not on the compile classpath of the component declaration — the variant dependency has to sit on the module that merges |
