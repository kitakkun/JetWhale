# Developing plugins

A JetWhale host plugin is a fat-jar that the JetWhale host loads at runtime. You develop it in your
**own** repository: compile against the published SDK, and use the published `com.kitakkun.jetwhale.host`
Gradle plugin to package it and to run a real host with your plugin loaded — with **hot reload**, so
you can edit your plugin and see it update without restarting the host.

The `com.kitakkun.jetwhale.host` plugin gives your plugin's module these tasks:

| Task                     | What it does                                                                                          |
|--------------------------|------------------------------------------------------------------------------------------------------|
| `packagePlugin`          | Builds the distributable plugin fat-jar (the artifact you drop into `~/.jetwhale/plugins/`).          |
| `installPlugin`          | Copies the packaged fat-jar into `~/.jetwhale/plugins/`.                                              |
| `stageDevPlugin`         | Stages the packaged fat-jar into a private dev directory the host watches for hot reload.             |
| `runJetWhale`            | Downloads a released JetWhale host for your OS and launches it with your plugin loaded.|
| `runJetWhaleHot`         | Like `runJetWhale`, but runs the host on the JetBrains Runtime so structural changes hot-reload in place (see [Limitations](#limitations)), and auto re-stages your plugin in the background — the whole hot-reload loop in one command. |

## Set up

### 1. Repositories

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
```

### 2. Apply the plugin and pin a host version

A JetWhale plugin module is a Kotlin/JVM module with Compose UI. Apply `com.kitakkun.jetwhale.host`, set
`hostVersion` to the released host you want to run against, and depend on the SDK at compile time only
(the host provides it at runtime):

```kotlin
// the plugin's host module — build.gradle.kts
plugins {
    kotlin("jvm") version "<kotlinVersion>"
    id("org.jetbrains.kotlin.plugin.compose") version "<kotlinVersion>"
    id("com.kitakkun.jetwhale.host") version "<version>"
}

dependencies {
    // Provided by the host at runtime, so compileOnly — they must NOT be bundled into the plugin jar.
    compileOnly("com.kitakkun.jetwhale:jetwhale-host-sdk:<version>")
    compileOnly("org.jetbrains.compose.material3:material3:<composeMaterial3Version>")
}

jetwhalePlugin {
    hostVersion.set("<version>")
}
```

::: warning Kotlin version compatibility
The host loads your plugin jar into its own runtime, which ships a fixed Kotlin stdlib and Compose
runtime. Kotlin is **not forward-compatible**: a plugin compiled with a newer Kotlin than the host's
may fail to load or crash at runtime. Build your plugin with the **same Kotlin (and Compose) version
as the host release you target** — check the host's release notes for the versions it was built
with. When in doubt, match the versions used by
[`jetwhale-plugins`](https://github.com/kitakkun/JetWhale/tree/main/jetwhale-plugins) at the
corresponding release tag.
:::

The agent SDK goes in the **app being debugged** (a normal runtime dependency, not in the plugin
module):

```kotlin
implementation("com.kitakkun.jetwhale:jetwhale-agent-sdk:<version>")
```

### 3. Write the plugin

Implement `JetWhaleHostPluginFactory` and declare it in a plugin manifest. The host loads each plugin
by instantiating the `factoryClass` named in the manifest. See `jetwhale-plugins/example/host` for a
complete, working example:

- `src/main/kotlin/.../MyPluginFactory.kt` — a `JetWhaleHostPluginFactory` returning your
  `JetWhaleHostPlugin`. It needs a public no-arg constructor so the host can instantiate it.
- `src/main/resources/META-INF/jetwhale/plugin-manifest.json` — one entry per plugin under `plugins`,
  each with `pluginId`, `pluginName`, `version`, and `factoryClass` (the fully-qualified name of the
  factory above):

  ```json
  {
    "plugins": [
      {
        "pluginId": "com.example.myplugin",
        "pluginName": "My Plugin",
        "version": "1.0.0",
        "factoryClass": "com.example.MyPluginFactory"
      }
    ]
  }
  ```

#### Multiple plugins in one module

A single module's jar can ship several plugins: add one entry per plugin to the `plugins` array, each
pointing at its own `factoryClass`. The plugins share the jar (and its classloader), and are loaded,
reloaded, and hot-redefined together.

### 4. Talk to the app (messaging)

A host plugin and its agent counterpart (a `JetWhaleAgentPlugin` with the **same `pluginId`**) exchange
messages over one **symmetric** channel. You define your messages as plain `@Serializable` classes in a
shared module and tag them by role:

- `JetWhaleEvent` — a fire-and-forget notification.
- `JetWhaleRequest<R>` — a request that expects a reply of type `R` (also a plain `@Serializable` class).

```kotlin
// either side -> the other
@Serializable
data class ButtonClicked(val count: Int) : JetWhaleEvent

// a request, with its reply declared as Pong
@Serializable
data object Ping : JetWhaleRequest<Pong>

// a reply: no marker, so it can't be sent on its own
@Serializable
data object Pong
```

A messaging plugin extends `JetWhaleMessagingHostPlugin` on the host (and `JetWhaleAgentPlugin` on the
agent). Both register handlers and send through a **messenger**:

- `configure { … }` to register handlers — `onEvent { e: E -> … }` for fire-and-forget events, and
  `onRequest { req: REQ -> … reply(r) }` for requests. A request handler must end with `reply(...)`
  of the request's declared reply type — replying is enforced by the handler's return type, so a
  path that forgets to reply (or replies with the wrong type) does not compile. The `reply(...)`
  expression is also what makes "this value goes back over the wire" visible at the registration site.
- a messenger to send — events with `trySend(event)` (drop if offline, returns `Boolean`),
  `sendOrQueue(event)` (buffer while offline and flush on reconnect), or `sendOrFail(event)` (throw if
  offline); plus `request(req): R` for request-reply (discard the result if you only need the call
  to succeed — e.g. a command whose reply is just an `Ack`).

```kotlin
class MyHostPlugin : JetWhaleMessagingHostPlugin(), JetWhaleHostPluginUi {
    override fun JetWhaleMessageHandlers.configure() {
        onEvent { e: ButtonClicked -> /* update UI state */ }
    }

    @Composable
    override fun Content() {
        Button(
            onClick = {
                pluginScope.launch {
                    messenger.request(Ping)
                }
            },
        ) {
            Text("Ping")
        }
    }
}
```

Requests work in **both directions** — the agent can `request` the host just as the host can `request`
the agent. A failed/timed-out request throws `JetWhaleRequestException` (and `sendOrFail`/`request`
throw `JetWhaleConnectionClosedException` while disconnected — both are `JetWhaleMessagingException`);
pass `timeout` to `request`
to override the default per call (e.g. `request(SlowOp, timeout = 30.seconds)`). Implement `JetWhaleHostPluginUi`
(`@Composable Content()`) to render a UI; plugins that don't are **headless** (e.g. MCP-only).

**The host plugin's `messenger` is a plain property.** A host plugin instance lives exactly as long
as its session's connection, so `messenger` is valid from `onCreate()` through `onDispose()` and may
be used anywhere — UI callbacks, MCP tool handlers, background jobs. Launch background work on
`pluginScope` (also a property): the runtime cancels it when the instance is disposed, so nothing
can outlive the plugin. State that must survive across sessions does **not** belong in the instance.

**On the agent** the `messenger` property is **connection-independent** (it outlives any single
connection), so app code may send at any time and choose the offline behavior per call: `trySend`
drops, `sendOrQueue` buffers, `sendOrFail` throws. Buffering is opt-in — override
`offlineEventBufferCapacity` (bounded, drops oldest when full).

**Commands vs queries.** `request` returns the reply value (`val r: Pong = request(Ping)`); when you
issue a command and only need it to succeed, just discard the result (`request(SetMockRules(rules))`).
The reply type is still inferred from the request's declaration, so the call is well-formed either way.

**Handlers reply via their return value.** The reply is sent when the handler returns, so don't do
slow post-reply work inline — compute the reply, offload the rest:

```kotlin
onRequest { req: SetMockRules ->
    applyRules(req.rules)
    pluginScope.launch { rebuildExpensiveIndex() } // after-reply work: don't keep the caller waiting
    reply(Ack)
}
```

**Agent lifecycle.** The **app** owns an agent plugin instance, so the runtime does not create or
dispose it — it **activates** and **deactivates** it: `onActivate()` (the host enabled the plugin) →
`onPrepare()` / `onDisconnected()` (each (re)connection within the activation) → `onDeactivate()`
(the host disabled it). A disconnect is **not** a deactivation: the plugin stays activated and keeps
buffering for the next connection. The runtime does **not** cancel the plugin's own coroutines, so
stop anything you started in `onActivate()` from `onDeactivate()`.

**Initial exchange: `onPrepare()`.** To exchange initial state on connect, override the suspend
`onPrepare()` on either side — plain `messenger` calls, no special scope. Until it returns, none of
that side's handlers are dispatched (inbound events and requests are held in arrival order) and the
agent's buffered `sendOrQueue` events are held — so handlers and the other side never observe
un-prepared state:

```kotlin
// host: fetch and adopt the config the agent holds (its config survives host restarts)
override suspend fun onPrepare() {
    val config = messenger.request(GetMockConfig)
    apply(config)
}
// agent: nothing to do — it just answers GetMockConfig from its handlers
```

By convention only **one** side of a plugin pair actively `request`s during preparation (the other
answers from its handlers): two sides that both block on each other's handlers while preparing would
deadlock until the timeout. `onPrepare` is bounded by `prepareTimeoutMillis` — on timeout (or
failure) the runtime logs a warning (visible in the host log) and opens handler dispatch anyway, so
the plugin proceeds degraded rather than hanging.

Note that there is no preparation-only message lane: a handler registered in `configure` is callable
by the other side for the **whole session**, not just while preparing. Design prepare-time exchanges
as **idempotent, read-only queries** (like `GetMockConfig`) so answering them again later is
harmless; if an exchange truly must happen only once, validate that in the handler and reply with an
error afterwards.

**Treat the other side's input as untrusted.** Because messaging is symmetric, a host `onRequest` /
`onEvent` handler runs on input the **agent** (the app being debugged) chose to send — and vice
versa. Validate payloads, and keep handlers cheap and non-blocking: the peer caps how many requests
run concurrently and rejects a flood with a failure reply, but a handler that blocks still holds its
slot, so offload slow work rather than stalling inside the handler.

#### Host-only plugins (no agent, no messaging)

If a plugin doesn't talk to the app at all — a host-side tool that just renders UI or uses the host's
own capabilities — extend the plain `JetWhaleHostPlugin` (not `JetWhaleMessagingHostPlugin`) and set
`"requiresAgent": false` in its manifest entry. Such a plugin has no agent counterpart and no
`messenger`; it is made available for every active session. See
`ExampleHostOnlyPlugin` in `jetwhale-plugins/example/host`.

## Hot reload (the live dev loop)

`runJetWhale` starts the host with `-Djetwhale.devPluginsDir=<dir>` pointing at a dev
directory under your module's `build` folder. The host loads plugins from that directory **in addition
to** `~/.jetwhale/plugins/` and watches it: whenever the plugin jar is re-staged, the host reloads it
and refreshes the open plugin screen — **no host restart needed**. For simple edits it redefines your
classes in place and keeps the plugin's state; for changes it can't apply that way it recreates the
plugin from a fresh classloader (see [Limitations](#limitations) below).

The simplest loop is a single command — `runJetWhaleHot` launches the host **and** keeps re-staging
your plugin for you:

```shell
# Launches the host and re-stages on every source change, all in one terminal.
./gradlew :myPlugin:runJetWhaleHot
```

It runs the host in the foreground and, in the background, a `stageDevPlugin -t` that re-packages and
re-stages the jar whenever you edit a source file; the host then hot-reloads it. The background
re-staging stops automatically when you stop the host (close it, or press Ctrl+C). `runJetWhaleHot`
also runs the host on the JetBrains Runtime so structural changes hot-reload in place — see
[Limitations](#limitations).

If you prefer a plain JDK (no JBR toolchain), use `runJetWhale` instead and drive the re-staging
yourself from a second terminal:

```shell
# Terminal 1 — download + launch the host (stays running)
./gradlew :myPlugin:runJetWhale

# Terminal 2 — rebuild & re-stage the plugin jar on every source change
./gradlew :myPlugin:stageDevPlugin -t
```

> Do **not** add `-t` to `runJetWhale`/`runJetWhaleHot`: they are long-running processes (they block
> until you close the host), and Gradle continuous mode only starts a new build once the current task
> graph finishes. `runJetWhaleHot` already runs the watcher for you; for `runJetWhale`, keep the host
> in one terminal and `stageDevPlugin -t` in another.

`runJetWhale` downloads the runnable host uber jar for `hostVersion` and the current
OS/architecture from the GitHub release (cached under `~/.jetwhale/dev-host/`) — no manual install of
JetWhale needed. Pass `-PjetwhaleHostJar=<path>` to launch a locally built host uber jar instead.

### Limitations

Hot reload always keeps you working without a host restart, but **how much of your plugin's in-memory
state survives** depends on the kind of change you made:

| Change you made                                                            | What happens                                              |
|----------------------------------------------------------------------------|-----------------------------------------------------------|
| Edit a **method body**                                                      | Redefined in place — the plugin instance and its state are **kept**. |
| **Structural** change: add/remove a method or field, change a signature or supertype | Can't be redefined in place → **full reload**; the plugin is recreated and its in-memory state **resets**. |
| Add a **new class/file**, or change **dependencies** (new jars)            | **Full reload** — the plugin's classloader is dropped and rebuilt from the new jar. |
| Change the **plugin manifest** (`pluginId`, icon, version)                 | Picked up on the next stage as a **full reload**.         |

Compose-specific: restructuring a `@Composable` (changing its group structure) can reset the state
held by that part of the UI even when the rest of the plugin is preserved.

On a **stock JDK**, only method-body edits are redefined in place; everything else falls back to a
full reload. To preserve state across **structural** changes too, launch with **`runJetWhaleHot`**,
which runs the host on the **JetBrains Runtime (JBR)** with enhanced class redefinition. JBR is
provisioned via Gradle toolchains — add the
[foojay resolver](https://github.com/gradle/foojay-toolchains) to your `settings.gradle.kts` so it
can be downloaded automatically:

```kotlin
plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }
```

So: a change that can't be redefined in place costs you the plugin's in-memory state — never a host
restart.

## Trying an unreleased (SNAPSHOT) build

Pre-release builds are published as `-SNAPSHOT`. To try one, add the Central snapshots repository to
**both** repository blocks in `settings.gradle.kts` and use a `-SNAPSHOT` version everywhere
(`id("com.kitakkun.jetwhale.host") version`, the SDK dependency, and `hostVersion`):

```kotlin
maven("https://central.sonatype.com/repository/maven-snapshots/")
```

## Developing inside this repository

In-repo plugin modules (e.g. `jetwhale-plugins/example/host`) don't download a host — they launch the
local `:jetwhale-host:app` project directly via `runJetWhaleLocal`, which is added by the internal,
non-published `jetwhale-host-launch` convention applied alongside `com.kitakkun.jetwhale.host`:

```shell
./gradlew :jetwhale-plugins:example:host:runJetWhaleLocal   # builds + launches the local host
./gradlew :jetwhale-plugins:example:host:stageDevPlugin -t
```

The hot-reload model is identical; only the source of the host differs (local project vs downloaded
release).

When the `jetwhale.devPluginsDir` system property is absent (i.e. a normal production launch), dev
mode and hot reload are completely inert — behaviour is unchanged.
