# Wiring with kotlin-inject-anvil, or an existing Square Anvil setup

Two unrelated libraries that happen to share a name and the word `replaces`. Both merge
contributions across the compile classpath, so the module layout and the seam are identical to
Metro's — read [`metro.md`](metro.md) first and treat this file as the delta.

- **kotlin-inject-anvil** (Amazon, actively developed) — the section below.
- **Square Anvil** — runs only on the K1 compiler, which keeps a project on Kotlin 2.2 or older,
  below JetWhale's Kotlin 2.3 floor. A Square Anvil project has to migrate before it can add
  JetWhale; see its section below.

The kotlin-inject-anvil shape was verified by building a four-module project (`:seam`, `:tooling`,
`:app-debug` depending on both, `:app-release` depending on `:seam` only) and running each app, with
stand-in classes in place of JetWhale's: release resolves the no-op, debug resolves the real
implementation, and a scoped holder is shared across two injection sites. That checks the DI shape,
not a JetWhale connection.

## kotlin-inject-anvil

Verified with kotlin-inject-anvil 0.1.7, kotlin-inject 0.9.0, KSP 2.3.10, Kotlin 2.3.10.

Annotations live under `software.amazon.lastmile.kotlin.inject.anvil`, with kotlin-inject's `@Inject`
(`me.tatarka.inject.annotations.Inject`) and `@SingleIn(AppScope::class)` for scoping. The component
is `@MergeComponent(AppScope::class)` on an abstract class, instantiated with
`AppComponent::class.create()`.

```kotlin
// production module
fun interface DebugToolingInitializer {
    fun initialize()
}

@ContributesBinding(AppScope::class)
@Inject
class NoOpInitializer : DebugToolingInitializer {
    override fun initialize() = Unit
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
    override fun initialize() {
        startJetWhale {
            connection { endpoints { ws("localhost", 5080) } }
            plugins { register(agents.network) }
        }
    }
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

## Square Anvil — migrate first

Square Anvil generates through a K1-only compiler plugin, and Kotlin 2.3 dropped language version
1.9, the last one that runs K1. A project on Square Anvil is therefore held at Kotlin 2.2, while
JetWhale needs Kotlin 2.3 or newer, so its artifacts do not load there.
`-Xskip-metadata-version-check` exists but is an unsupported escape hatch, not a plan.

Before adding JetWhale, the project needs to leave Square Anvil: for Metro, its successor, or for a
KSP-based Anvil fork such as anvil-ksp. The seam then transfers with only the annotation packages
changed, and on Metro `@Multibinds(allowEmpty = true)` lets the HTTP decorator be a multibinding
again. Say this to the team rather than starting the migration on your own; it is a decision for
them.

## Common failure modes

| Symptom | Cause |
|---|---|
| Duplicate binding for the seam type | `replaces` missing, or the contributions target different scopes |
| Release cannot resolve the seam | The no-op is in the debug-only module |
| `Cannot find an @Inject constructor or provider for: Set<…>` | kotlin-inject-anvil: an empty multibinding. Use `replaces` with a no-op instead |
| Contribution silently ignored | The debug module is not on the compile classpath of the component declaration — the variant dependency has to sit on the module that merges |
