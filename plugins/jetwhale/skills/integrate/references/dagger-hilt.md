# Wiring with Dagger or Hilt

Neither merges contributions off the classpath, so there is no `replaces` to lean on. The switch is
made by **supplying the binding from a different place per variant**: one module for debug, one for
release, providing the same type.

Android variant source sets make this cheap — `src/debug/` and `src/release/` inside a single
module, with `debugImplementation` for the JetWhale dependency. Nothing in `src/main/` ever names
JetWhale, so the release compilation never needs it.

## Layout

```
:app
  build.gradle.kts        debugImplementation("com.kitakkun.jetwhale:jetwhale-agent-runtime:<version>")
  src/main/…/DebugToolingInitializer.kt    ← the seam, always compiled
  src/debug/…/DebugToolingModule.kt        ← binds JetWhaleInitializer
  src/release/…/DebugToolingModule.kt      ← binds NoOpInitializer
```

Both module files must declare the **same binding key**, and Hilt requires the same
`@InstallIn` component. Keeping the file name and class name identical across the two source sets
is what makes the pair obvious to the next reader.

## The seam

```kotlin
// src/main
interface DebugToolingInitializer {
    fun initialize()
}
```

## Release side

```kotlin
// src/release
@Module
@InstallIn(SingletonComponent::class)
object DebugToolingModule {
    @Provides
    @Singleton
    fun provideInitializer(): DebugToolingInitializer =
        object : DebugToolingInitializer {
            override fun initialize() = Unit
        }
}
```

## Debug side

```kotlin
// src/debug — the only source set that imports JetWhale
@Module
@InstallIn(SingletonComponent::class)
object DebugToolingModule {
    @Provides
    @Singleton
    fun provideNetworkAgent(): JetWhaleNetworkAgentPlugin = JetWhaleNetworkAgentPlugin()

    @Provides
    @Singleton
    fun provideInitializer(agent: JetWhaleNetworkAgentPlugin): DebugToolingInitializer =
        DebugToolingInitializer {
            startJetWhale {
                connection { host = "localhost"; port = 5080 }
                plugins { register(agent) }
            }
        }
}
```

`@Singleton` on the agent provider is what keeps one instance across the two call sites.

## HTTP client capture

Use an optional multibinding so the release side contributes nothing:

```kotlin
// src/main — declares the (possibly empty) set
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
fun provideJetWhaleDecorator(agent: JetWhaleNetworkAgentPlugin): HttpClientDecorator =
    HttpClientDecorator { client ->
        client.plugin(HttpSend).intercept(agent.ktorSendInterceptor(client))
    }
```

The production `HttpClient` / `OkHttpClient` provider injects `Set<HttpClientDecorator>` and applies
every element. In release the set is empty and the provider is unchanged.

## Plain Dagger, or a KMP project

Without Hilt, the same split applies to the component's `modules = [...]` list: declare
`DebugToolingModule` in both source sets and list it once in the component.

KMP has no variant source sets. Put the debug module in its own Gradle module and gate the
dependency on a Gradle property, or keep two thin entry-point modules — see the KMP section of
[`metro.md`](metro.md), which is framework-independent.

## Failure modes

| Symptom | Cause |
|---|---|
| `DuplicateBindings` | Both source sets ended up on one compile — check that `src/debug` is not also listed in the release variant's source sets |
| `MissingBinding` in release only | The release module file is missing, or its `@InstallIn` component differs from the debug one |
| Unresolved reference to JetWhale in release | Something in `src/main` names a JetWhale type — the seam leaked |
| Session connects, no traffic | Two agent instances — `@Singleton` missing on the agent provider |
