# Wiring without a DI framework

A project with no container does not need one to keep JetWhale out of release builds. The seam is a
plain interface and a variant-specific factory — roughly ten lines, no new dependency in production
code.

Do **not** introduce a DI framework for this. Adding a container to an app that has deliberately
avoided one is a far larger change than the integration it would serve, and it is not yours to make.

## Android — variant source sets

```kotlin
// src/main — production code, no JetWhale anywhere
interface DebugTooling {
    fun initialize()
}

object DebugToolingHolder {
    val instance: DebugTooling by lazy { createDebugTooling() }
}
```

`createDebugTooling()` is declared once per variant, with the same signature:

```kotlin
// src/release
internal fun createDebugTooling(): DebugTooling = object : DebugTooling {
    override fun initialize() = Unit
}
```

```kotlin
// src/debug — the only source set importing JetWhale
internal fun createDebugTooling(): DebugTooling = object : DebugTooling {
    private val networkAgent = JetWhaleNetworkAgentPlugin()

    override fun initialize() {
        startJetWhale {
            connection { endpoints { ws("localhost", 5080) } }
            plugins { register(networkAgent) }
        }
    }

    // HTTP client capture adds decorate(); see below
}
```

Call it once, from `Application.onCreate()`:

```kotlin
DebugToolingHolder.instance.initialize()
```

The Gradle side is the runtime plus the network agent this sample registers:

```kotlin
dependencies {
    debugImplementation("com.kitakkun.jetwhale:jetwhale-agent-runtime:<version>")
    debugImplementation("com.kitakkun.jetwhale:jetwhale-network-inspector-agent-ktor:<version>")
}
```

Verified on AGP 9.3.0 / Kotlin 2.4.10, with stand-in classes, by reading the built APKs rather than
the wiring: the debug
APK's dex carried the debug-only class and the `"jetwhale:"` marker, while the release APK carried
`"noop"` and zero occurrences of the debug-only class. `:tooling` appeared on
`debugRuntimeClasspath` and was absent from `releaseRuntimeClasspath`.

## HTTP client capture

The client is built in production code, so give the seam a method that can do nothing, and
implement it on both sides:

```kotlin
// src/main
interface DebugTooling {
    fun initialize()
    fun decorate(client: HttpClient)
}
```

```kotlin
// src/release
internal fun createDebugTooling(): DebugTooling = object : DebugTooling {
    override fun initialize() = Unit
    override fun decorate(client: HttpClient) = Unit
}
```

```kotlin
// src/debug
internal fun createDebugTooling(): DebugTooling = object : DebugTooling {
    private val networkAgent = JetWhaleNetworkAgentPlugin()

    override fun initialize() {
        startJetWhale {
            connection { endpoints { ws("localhost", 5080) } }
            plugins { register(networkAgent) }
        }
    }

    override fun decorate(client: HttpClient) {
        client.plugin(HttpSend).intercept(networkAgent.ktorSendInterceptor(client))
    }
}
```

```kotlin
// src/main — where the client is built
val client = HttpClient().also { DebugToolingHolder.instance.decorate(it) }
```

`HttpClient` here is Ktor's type, which production code already depends on — that is what makes it
safe to name in the seam. The rule is only that **JetWhale** types stay out of it.

The debug implementation holds the agent as a field, so `initialize()` and `decorate()` share one
instance. Two instances is the classic mistake: the session connects and no traffic ever appears.

## KMP — no variant source sets

`src/debug` is an Android Gradle Plugin feature; `expect`/`actual` splits by *platform*, not by
build type, so neither helps. Two options:

1. **A debug-only Gradle module found at runtime, on JVM targets.** The JetWhale implementation
   lives in a debug-only module, with the dependency gated on a Gradle property. Production code
   cannot name that module, so it looks the implementation up with `java.util.ServiceLoader`:

   ```kotlin
   // production (jvmMain)
   interface DebugToolingProvider {
       fun create(): DebugTooling
   }

   object NoOpDebugTooling : DebugTooling {
       override fun initialize() = Unit
       override fun decorate(client: HttpClient) = Unit
   }

   object DebugToolingHolder {
       val instance: DebugTooling by lazy {
           ServiceLoader.load(DebugToolingProvider::class.java).firstOrNull()?.create() ?: NoOpDebugTooling
       }
   }
   ```

   ```kotlin
   // debug module (jvmMain)
   class JetWhaleDebugToolingProvider : DebugToolingProvider {
       override fun create(): DebugTooling = JetWhaleDebugTooling()
   }
   ```

   `JetWhaleDebugTooling` is the debug implementation from the Android section, written as a class.
   The debug module registers the provider in
   `src/jvmMain/resources/META-INF/services/<DebugToolingProvider's fully qualified name>`, a file
   whose one line is `JetWhaleDebugToolingProvider`'s fully qualified name. Other targets have no
   `ServiceLoader`: there a debug-only entry point has to hand the implementation over, and option 2
   is the simpler shape.

2. **Two thin entry-point modules** — `:app-debug` and `:app-release`, each with its own `main()`
   that wires what it needs. More files, but no lookup at runtime.

Option 2 is usually the better fit for a Compose Multiplatform desktop app, where the entry point is
already tiny. Option 1 fits a JVM app whose one entry point serves both builds.

## Failure modes

| Symptom | Cause |
|---|---|
| Release build cannot resolve `createDebugTooling` | Only the debug source set defines it — both variants need one |
| Unresolved JetWhale reference in release | A JetWhale type reached the seam or `src/main` |
| Session connects, no traffic | `initialize()` and `decorate()` are using different agent instances |
| Nothing appears in the host | The holder was never touched, so `by lazy` never ran — confirm the call site is actually reached |
