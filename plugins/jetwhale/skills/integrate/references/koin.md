# Wiring with Koin

Koin resolves at runtime, so nothing stops a release build from *compiling* against JetWhale — the
compiler will not catch a leak for you. The discipline has to come from the module layout: the
seam's implementation lives in a variant-specific file, and the JetWhale dependency is
variant-specific too, so a leak fails the release build at compile time rather than shipping.

## The seam

```kotlin
// src/main — production code
interface DebugToolingInitializer {
    fun initialize()
}
```

## One Koin module per variant

Declare the same `Module` value in both source sets, and load it once from `startKoin`.

```kotlin
// src/release
val debugToolingModule = module {
    single<DebugToolingInitializer> {
        object : DebugToolingInitializer {
            override fun initialize() = Unit
        }
    }
}
```

```kotlin
// src/debug — the only source set importing JetWhale
val debugToolingModule = module {
    single { JetWhaleNetworkAgentPlugin() }
    single<DebugToolingInitializer> {
        val agent: JetWhaleNetworkAgentPlugin = get()
        DebugToolingInitializer {
            startJetWhale {
                connection { host = "localhost"; port = 5080 }
                plugins { register(agent) }
            }
        }
    }
}
```

```kotlin
// src/main — unchanged between variants
startKoin {
    modules(appModule, networkModule, debugToolingModule)
}

get<DebugToolingInitializer>().initialize()
```

`single { }` — not `factory { }` — is what keeps one `JetWhaleNetworkAgentPlugin` across the HTTP
client and the session. A `factory` here produces the "session connects, no traffic" bug.

## HTTP client capture

Koin has no multibinding, so give the production client provider a list it can resolve in both
variants:

```kotlin
// src/main — production
val networkModule = module {
    single { HttpClient().also { client -> getAll<HttpClientDecorator>().forEach { it.decorate(client) } } }
}
```

`getAll<T>()` returns every definition of the type and is empty when none are declared, which is
exactly the release case. Declare the decorator only in `src/debug`:

```kotlin
// src/debug
single<HttpClientDecorator> {
    val agent: JetWhaleNetworkAgentPlugin = get()
    HttpClientDecorator { client -> client.plugin(HttpSend).intercept(agent.ktorSendInterceptor(client)) }
}
```

Check `getAll`'s behaviour in the project's Koin version before relying on it; if it is unavailable,
an explicit `getOrNull<HttpClientDecorator>()` with a nullable single works just as well and reads
more plainly.

## Gradle

```kotlin
dependencies {
    debugImplementation("com.kitakkun.jetwhale:jetwhale-agent-runtime:<version>")
    debugImplementation("com.kitakkun.jetwhale:jetwhale-network-inspector-agent-ktor:<version>")
}
```

KMP without Android variants has no `src/debug`; put the debug Koin module in its own Gradle module
and gate the dependency on a Gradle property — see the KMP section of [`metro.md`](metro.md).

## Failure modes

| Symptom | Cause |
|---|---|
| `NoDefinitionFoundException` for the seam in release | The release-side module file is missing, or `debugToolingModule` was not passed to `modules(...)` |
| Release compiles but ships JetWhale | The dependency was added with `implementation`, not `debugImplementation` |
| Session connects, no traffic | `factory` instead of `single` for the agent, or two separate `single` definitions |
| Runtime crash only in release | Koin resolves at runtime — a missing definition is not a compile error. Launch the release build once before calling it done |
