# Wiring with Metro

Metro merges contributions at the `@DependencyGraph` declaration by scanning that compilation's
classpath, and `@ContributesBinding(replaces = ...)` lets one contribution displace another. That is
exactly the seam: contribute a no-op from a module that is always present, and a JetWhale-backed
implementation from a module that only exists on the debug classpath.

The variant-specific dependency therefore belongs on **the module that declares the graph**, usually
the app module.

Verified with Metro 1.3.2 and Kotlin 2.4.10, by running a four-module project both ways: the release
app resolved `noop` with an empty decorator set, the debug app resolved the JetWhale binding, and
the `@SingleIn` holder reported the same instance identity through both the initializer and the
decorator.

## Module layout

```
:app                  @DependencyGraph(AppScope::class)
 │                     implementation(:core:debug)
 │                     debugImplementation(:debug-jetwhale)
 │
 ├── :core:debug      ← every classpath
 └── :debug-jetwhale  ← debug classpath only; the only module importing JetWhale
```

## 1. The seam, in production code

```kotlin
// :core:debug
fun interface DebugToolingInitializer {
    fun initialize()
}

@ContributesBinding(AppScope::class)
@Inject
class NoOpInitializer : DebugToolingInitializer {
    override fun initialize() = Unit
}
```

## 2. The JetWhale side

```kotlin
// :debug-jetwhale
@SingleIn(AppScope::class)
@Inject
class JetWhaleAgents {
    val network: JetWhaleNetworkAgentPlugin = JetWhaleNetworkAgentPlugin()
}

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

`@SingleIn(AppScope::class)` on `JetWhaleAgents` is what guarantees the plugin registered with the
session is the same one installed into the HTTP client.

## 3. The call site

```kotlin
appGraph.debugToolingInitializer.initialize()
```

Add the accessor to the graph interface. In release this calls an empty method, which R8 removes
from a minified Android build.

## 4. HTTP client capture — a multibinding, not a replacement

Startup is one binding, so `replaces` fits. Client customization may have zero contributions, which
is what a multibinding models: the debug module contributes an element, release contributes none.
No `replaces` involved.

```kotlin
// :core:debug
fun interface HttpClientDecorator {
    fun decorate(client: HttpClient)
}

@ContributesTo(AppScope::class)
interface HttpClientDecoratorDeclarations {
    // Empty multibindings are an error by default, and release contributes nothing.
    @Multibinds(allowEmpty = true)
    fun httpClientDecorators(): Set<HttpClientDecorator>
}
```

```kotlin
// :core:network — production code, still JetWhale-free
@ContributesTo(AppScope::class)
interface NetworkBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun provideHttpClient(decorators: Set<HttpClientDecorator>): HttpClient =
        HttpClient().also { client -> decorators.forEach { it.decorate(client) } }
}
```

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

`HttpSend` rather than `install(...)` because the client arrives from the graph already built.
Register once per client — `HttpSend` accepts duplicate interceptors silently and records every
transaction twice.

OkHttp is the same shape: contribute an `Interceptor` with `@ContributesIntoSet`, and have the
production `OkHttpClient` provider add every element of the (possibly empty) set as an application
interceptor.

## Gradle

`:debug-jetwhale` implements `DebugToolingInitializer` and names `NoOpInitializer`, so it depends on
`:core:debug` itself; `:app` depending on both does not put one on the other's classpath:

```kotlin
// :debug-jetwhale (in a KMP module, inside commonMain.dependencies { })
dependencies {
    implementation(projects.core.debug)
    implementation("com.kitakkun.jetwhale:jetwhale-agent-runtime:<version>")
    implementation("com.kitakkun.jetwhale:jetwhale-network-inspector-agent-ktor:<version>")
}
```

```kotlin
// :app, Android
dependencies {
    implementation(projects.core.debug)
    debugImplementation(projects.debugJetwhale)
}
```

KMP has no variants. Two thin entry-point modules, `:app-debug` and `:app-release`, each declaring
its own graph, keep a release clean however the build is invoked. The lighter alternative gates the
dependency on a property:

```kotlin
// :app, KMP — gate on a property
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

The property switches the whole invocation, not a build type: while it is set, every compilation in
that run gets `:debug-jetwhale`, release binaries included. Pass `-Pjetwhale.enabled=true` on debug
runs only, never from a `gradle.properties` file, and build releases in runs that leave it out.
Flipping it changes the compile classpath, so the graph module recompiles.

## Failure modes

| Symptom | Cause |
|---|---|
| `Metro/DuplicateBinding` naming both implementations | `replaces` missing, or the two contributions target different scopes |
| Release build cannot resolve `DebugToolingInitializer` | The no-op lives in the debug-only module; move it to the always-present one |
| Empty-multibinding error in release | `@Multibinds(allowEmpty = true)` missing, or declared in the debug module instead of production code |
| Session connects, no traffic | Two `JetWhaleNetworkAgentPlugin` instances — check `@SingleIn` on the holder |

`replaces` names the **contributing class** (`NoOpInitializer`), never the bound type
(`DebugToolingInitializer`).
