# Wiring with Dagger or Hilt

Neither has Metro's `replaces`. What they have instead is **optional bindings**: production code
declares that a binding *may be absent*, and the debug side is the only one that supplies it. That
is a better fit than it sounds — the release behaviour this seam wants is "do nothing", which is
exactly what absence means.

Whether you need a release-side counterpart at all comes down to one difference:

| | How the debug module reaches the graph | Release-side counterpart |
|---|---|---|
| **Hilt** | Discovered from `@InstallIn` on the classpath | **Not needed** — one-sided |
| **Plain Dagger** | Listed explicitly in `@Component(modules = [...])` | **Required** — a same-name module per variant |

Verified with Dagger 2.60.1, Hilt 2.60.1, AGP 9.3.0, Kotlin 2.4.10, KSP 2.3.10.

## Hilt — one-sided, no release counterpart

Declare the optional binding once, in `src/main`:

```kotlin
// src/main — production code, never names JetWhale
interface DebugToolingInitializer {
    fun initialize(): String
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SeamModule {
    @BindsOptionalOf
    abstract fun optionalInitializer(): DebugToolingInitializer
}
```

Supply it from `src/debug` only. There is no `src/release` file:

```kotlin
// src/debug — the only source set that imports JetWhale
@Module
@InstallIn(SingletonComponent::class)
object JetWhaleModule {
    @Provides
    @Singleton
    fun agents(): JetWhaleAgents = JetWhaleAgents()

    @Provides
    fun initializer(agents: JetWhaleAgents): DebugToolingInitializer =
        DebugToolingInitializer {
            startJetWhale {
                connection { host = "localhost"; port = 5080 }
                plugins { register(agents.network) }
            }
        }
}
```

Inject `Optional<DebugToolingInitializer>` and call it:

```kotlin
// src/main
@Inject lateinit var initializer: Optional<DebugToolingInitializer>

initializer.ifPresent { it.initialize() }
```

`@Singleton` on the agent provider is what keeps one instance across the HTTP client and the session.

This works because Hilt aggregates every `@InstallIn` module it finds on the variant's classpath.
The generated component proves it — same app, two variants:

```java
// app/build/generated/hilt/component_sources/debug/…/DaggerApp_HiltComponents_SingletonC.java
App_MembersInjector.injectInitializer(instance, Optional.of(debugToolingInitializer()));

// …/release/…
App_MembersInjector.injectInitializer(instance, Optional.empty());
```

The release component contains no reference to `JetWhaleModule` at all, and no
`JetWhaleModule_*Factory` is generated for that variant.

## Plain Dagger — the same-name module pair

Without Hilt, `@Component(modules = [...])` names its modules explicitly, and `src/main` cannot name
a class that exists only in `src/debug`. So the module has to exist in both source sets under the
same fully-qualified name — empty in release, providing in debug.

```kotlin
// src/main
@Singleton
@Component(modules = [SeamModule::class, DebugToolingModule::class])
interface AppComponent {
    fun initializer(): Optional<DebugToolingInitializer>
}
```

`SeamModule` (with `@BindsOptionalOf`) stays in `src/main`; `DebugToolingModule` is declared twice —
empty in `src/release`, providing in `src/debug`. Nothing keeps that pair in sync but convention, so
build both variants in CI.

## HTTP client capture

Dagger's `@Multibinds` allows an empty set **by default** — unlike Metro, there is no `allowEmpty`
parameter to set:

```kotlin
// src/main
@Module
@InstallIn(SingletonComponent::class)
abstract class HttpClientDecoratorModule {
    @Multibinds
    abstract fun decorators(): Set<HttpClientDecorator>
}
```

```kotlin
// src/debug
@Provides
@IntoSet
fun jetwhaleDecorator(agents: JetWhaleAgents): HttpClientDecorator =
    HttpClientDecorator { client ->
        client.plugin(HttpSend).intercept(agents.network.ktorSendInterceptor(client))
    }
```

The production `HttpClient` / `OkHttpClient` provider injects `Set<HttpClientDecorator>` and applies
every element. In release the set is empty and the provider is unchanged.

## Gradle, and what it actually buys you

```kotlin
dependencies {
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    debugImplementation(project(":tooling"))   // or the JetWhale artifacts directly
}
```

Verified end to end on the built artifacts, not just the wiring:

```
:app:dependencies --configuration releaseRuntimeClasspath   → no :tooling
:app:dependencies --configuration debugRuntimeClasspath     → +--- project :tooling
debug APK   dex strings: FakeAgents ×5
release APK dex strings: FakeAgents ×0
```

KMP has no variant source sets. Put the debug module in its own Gradle module and gate the
dependency on a Gradle property — see the KMP section of [`metro.md`](metro.md), which is
framework-independent.

## Failure modes

| Symptom | Cause |
|---|---|
| `MissingBinding` in release only | Plain Dagger with no release-side counterpart module, or `@BindsOptionalOf` never declared |
| `DuplicateBindings` | Both source sets ended up on one compile — check the release variant's source set list |
| Unresolved JetWhale reference in release | Something in `src/main` names a JetWhale type — the seam leaked |
| Session connects, no traffic | Two agent instances — `@Singleton` missing on the agent provider |
| `The 'org.jetbrains.kotlin.android' plugin is no longer required since AGP 9.0` | AGP 9 has built-in Kotlin; drop the plugin rather than pinning AGP back |
