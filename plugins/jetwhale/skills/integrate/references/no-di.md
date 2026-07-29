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
    fun start()
}

object DebugToolingHolder {
    val instance: DebugTooling by lazy { createDebugTooling() }
}
```

`createDebugTooling()` is declared once per variant, with the same signature:

```kotlin
// src/release
internal fun createDebugTooling(): DebugTooling = object : DebugTooling {
    override fun start() = Unit
}
```

```kotlin
// src/debug — the only source set importing JetWhale
internal fun createDebugTooling(): DebugTooling = object : DebugTooling {
    private val networkAgent = JetWhaleNetworkAgentPlugin()

    override fun start() {
        startJetWhale {
            connection { host = "localhost"; port = 5080 }
            plugins { register(networkAgent) }
        }
    }

    // expose the agent if the HTTP client needs it — see below
}
```

Call it once, from `Application.onCreate()`:

```kotlin
DebugToolingHolder.instance.start()
```

The Gradle side is a single line:

```kotlin
dependencies {
    debugImplementation("com.kitakkun.jetwhale:jetwhale-agent-runtime:<version>")
}
```

## HTTP client capture

The client is built in production code, so give the seam a method that can do nothing:

```kotlin
// src/main
interface DebugTooling {
    fun start()
    fun decorate(client: HttpClient)   // release: empty body
}
```

```kotlin
// src/main — where the client is built
val client = HttpClient().also { DebugToolingHolder.instance.decorate(it) }
```

`HttpClient` here is Ktor's type, which production code already depends on — that is what makes it
safe to name in the seam. The rule is only that **JetWhale** types stay out of it.

The debug implementation holds the agent as a field, so `start()` and `decorate()` share one
instance. Two instances is the classic mistake: the session connects and no traffic ever appears.

## KMP — no variant source sets

`src/debug` is an Android Gradle Plugin feature; `expect`/`actual` splits by *platform*, not by
build type, so neither helps. Two options:

1. **A debug-only Gradle module** holding the JetWhale implementation, with the dependency gated on
   a Gradle property. Production code then needs a way to find the implementation without naming
   it — a `ServiceLoader`-style lookup, or an `init` block in the debug module that registers
   itself into a mutable holder in production code:

   ```kotlin
   // production
   object DebugToolingHolder {
       var instance: DebugTooling = NoOpDebugTooling
   }
   ```

   ```kotlin
   // debug module, called explicitly from the debug entry point
   DebugToolingHolder.instance = JetWhaleDebugTooling()
   ```

2. **Two thin entry-point modules** — `:app-debug` and `:app-release`, each with its own `main()`
   that wires what it needs. More files, but no mutable global and no reflection.

Option 2 is usually the better fit for a Compose Multiplatform desktop app, where the entry point is
already tiny. Option 1 fits when the entry point is shared and platform-specific.

## Failure modes

| Symptom | Cause |
|---|---|
| Release build cannot resolve `createDebugTooling` | Only the debug source set defines it — both variants need one |
| Unresolved JetWhale reference in release | A JetWhale type reached the seam or `src/main` |
| Session connects, no traffic | `start()` and `decorate()` are using different agent instances |
| Nothing appears in the host | The holder was never touched, so `by lazy` never ran — confirm the call site is actually reached |
