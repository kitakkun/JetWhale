# Wiring with Anvil / kotlin-inject-anvil

Both frameworks merge contributions across the compile classpath and both spell displacement
`replaces`, so the module layout and the seam are identical to Metro's — read
[`metro.md`](metro.md) first and treat this file as the delta. The differences are annotation
packages, the injection annotation, and how multibindings are expressed.

**Verify against the version the project actually uses.** These libraries move, and
kotlin-inject-anvil in particular has changed the shape of its multibinding support across
releases. Check the annotation's parameters in the resolved artifact before relying on them.

## Square Anvil (Dagger-backed)

```kotlin
// production module
interface DebugToolingInitializer {
    fun initialize()
}

@ContributesBinding(AppScope::class)
class NoOpInitializer @Inject constructor() : DebugToolingInitializer {
    override fun initialize() = Unit
}
```

```kotlin
// debug-only module
@ContributesBinding(AppScope::class, replaces = [NoOpInitializer::class])
@Singleton
class JetWhaleInitializer @Inject constructor(
    private val agents: JetWhaleAgents,
) : DebugToolingInitializer {
    override fun initialize() { /* startJetWhale { … } */ }
}
```

- `@ContributesMultibinding(AppScope::class)` is the equivalent of Metro's `@ContributesIntoSet`
  for the HTTP-client decorator set.
- An empty `Set<HttpClientDecorator>` needs a `@Multibinds` declaration in a `@ContributesTo`
  module in production code — Dagger errors on an undeclared empty set the same way Metro does.
- Anvil is in maintenance mode; if the project is on a recent Kotlin and considering a move, Metro
  is the successor and this wiring transfers with only the annotation packages changed.

## kotlin-inject-anvil

Same annotation names under `software.amazon.lastmile.kotlin.inject.anvil`, with kotlin-inject's
`@Inject` (`me.tatarka.inject.annotations.Inject`) and `@SingleIn(AppScope::class)` for scoping.

```kotlin
@ContributesBinding(AppScope::class, replaces = [NoOpInitializer::class])
@Inject
@SingleIn(AppScope::class)
class JetWhaleInitializer(
    private val agents: JetWhaleAgents,
) : DebugToolingInitializer {
    override fun initialize() { /* startJetWhale { … } */ }
}
```

For the decorator set, kotlin-inject-anvil expresses multibinding contributions through
`@ContributesBinding`'s multibinding support rather than a separate annotation — confirm the exact
parameter in the project's version. If it is absent or awkward, skip the multibinding entirely: a
single `HttpClientDecorator` binding with a no-op default, displaced by `replaces` exactly like the
initializer, does the same job with one mechanism instead of two.

## Common failure modes

| Symptom | Cause |
|---|---|
| Duplicate binding for the seam type | `replaces` missing, or the contributions target different scopes |
| Release cannot resolve the seam | The no-op is in the debug-only module |
| Contribution silently ignored | The debug module is not on the compile classpath of the component/graph declaration — the variant dependency has to sit on the module that merges |
