# Developing plugins

A JetWhale host plugin is a fat-jar that the JetWhale host loads at runtime. You develop it in your
**own** repository: compile against the published SDK, and use the published `com.kitakkun.jetwhale.host`
Gradle plugin to package it and to run a real host with your plugin loaded — with **hot reload**, so
you can edit your plugin and see it update without restarting the host.

The `com.kitakkun.jetwhale.host` plugin gives your plugin's module these tasks:

| Task                     | What it does                                                                                          |
|--------------------------|------------------------------------------------------------------------------------------------------|
| `packagePlugin`          | Builds the distributable plugin fat-jar (the artifact you drop into `~/.jetwhale/plugins/`). Hooked to `assemble`, so a plain `./gradlew build` already produces it. |
| `installPlugin`          | Copies the packaged fat-jar into `~/.jetwhale/plugins/` — your real installation, not the [sandbox](#isolated-sandbox-environment). |
| `stageDevPlugin`         | Stages the packaged fat-jar into a private dev directory the host watches for hot reload.             |
| `packageMavenPlugin`     | Builds the publishable plugin jar (see [Publishing your plugin to Maven](#publishing-your-plugin-to-maven)). |
| `generatePluginDependencyManifest` | Writes the runtime dependency list `packageMavenPlugin` embeds. Runs as part of it. |
| `downloadJetWhaleHost`   | Downloads the released host uber jar for `hostVersion`. Runs as part of `runJetWhale`.                |
| `runJetWhale`            | Downloads a released JetWhale host for your OS and launches it with your plugin loaded.|
| `runJetWhaleHot`         | Like `runJetWhale`, but runs the host on the JetBrains Runtime so structural changes hot-reload in place (see [Limitations](#limitations)), and auto re-stages your plugin in the background — the whole hot-reload loop in one command. |
| `runJetWhaleQaAgent`     | Runs a headless debuggee your plugin can render against, driven over HTTP — see [QA Agent](/guide/qa-agent). |

Pass `--args="--headless"` to `runJetWhale` to launch the host without its window, which is how a
plugin is exercised on a machine with no display — see
[Headless mode](/guide/host-settings#headless-mode).

`runJetWhale` and `runJetWhaleHot` launch the host on a **Java 21** toolchain (`runJetWhaleHot`
additionally requires the JetBrains vendor), independently of the toolchain your plugin module
itself compiles with.

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

The extension has exactly three properties:

| Property | Default | What it does |
|----------|---------|--------------|
| `hostVersion` | none | The released host version `runJetWhale` / `runJetWhaleHot` download and launch. Required unless you pass `-PjetwhaleHostJar`. |
| `pluginArchiveName` | the project name | Base name of the packaged jars. Set it when several plugin modules in one build are all called `host`, so their jars do not collide in `~/.jetwhale/plugins/` or the staging directory. |
| `qaAgentVersion` | falls back to `hostVersion` | Version of the [QA agent](/guide/qa-agent) `runJetWhaleQaAgent` resolves. |

The two packaging tasks write `<pluginArchiveName>-<version>-jetwhale-plugin.jar` and
`<pluginArchiveName>-<version>-jetwhale-maven-plugin.jar` into `build/libs/`.

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
    "$schema": "https://raw.githubusercontent.com/kitakkun/JetWhale/main/schemas/plugin-manifest.schema.json",
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

The manifest is **hand-written** — the Gradle plugin ships whatever is in your resources rather than
generating it. Pointing `"$schema"` at the published JSON Schema gives you completion and validation
in the IDE.

#### Manifest reference

| Field | Required | Default | Meaning |
|-------|----------|---------|---------|
| `pluginId` | ✅ | — | Unique id. The **agent** plugin's `pluginId` must match it for the two to be paired. |
| `pluginName` | ✅ | — | Display name in the plugin drawer. |
| `version` | ✅ | — | Your plugin's version. |
| `factoryClass` | ✅ | — | Fully-qualified `JetWhaleHostPluginFactory` the host instantiates. Needs a public no-arg constructor. |
| `requiresAgent` | | `true` | `false` makes the plugin [host-only](#host-only-plugins-no-agent-no-messaging): no agent counterpart, no messaging, instantiated for every active session. |
| `agentVersionRange` | | none | `{ "min": …, "max": … }`, both **inclusive** and both nullable. An agent plugin whose `pluginVersion` falls outside the range is reported back to the agent as *incompatible* and never paired. Omit the object to accept any agent version. |
| `icon` | | none | `{ "activePath": …, "inactivePath": … }` — see below. |

#### Icons

`activePath` and `inactivePath` are resource paths **relative to the jar root** (e.g.
`"icons/widget_filled.svg"`), loaded with your plugin's own classloader. `activePath` is used when
the plugin is selected and enabled, `inactivePath` otherwise; a path that does not resolve falls back
silently to JetWhale's default puzzle-piece icons.

Both must be **SVG** — a PNG will not render — and they are drawn as Material `Icon`s, i.e. tinted
with the drawer's content color. Author monochrome shapes, not multi-color artwork.

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
- a messenger to send — fire-and-forget events with `trySend(event)` (returns `Boolean`), plus
  `request(req): R` for request-reply (discard the result if you only need the call to succeed —
  e.g. a command whose reply is just an `Ack`). The **agent** messenger additionally offers offline
  send policies (`sendOrQueue` / `sendOrFail`); the **host** messenger is always connected and does
  not (see below).

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
(`@Composable Content()`) to render a UI; plugins that don't are **headless** (e.g. MCP-only). Opening a
headless plugin in the host shows a "this plugin has no screen" notice instead of an empty view, and
the host does not offer to pop it out into a window.

::: tip `onDispose()` is public
`onCreate()` is `protected`, but `onDispose()` is `public open` — override it without a `protected`
modifier or it will not compile.
:::

**The host plugin's `messenger` is a plain, always-connected property.** A host plugin instance
lives exactly as long as its session's connection, so `messenger` is valid from `onCreate()` through
`onDispose()` and may be used anywhere — UI callbacks, MCP tool handlers, background jobs. Because
the instance only exists while its session is connected, the host messenger is **always connected**
for the instance's lifetime and exposes only `trySend` and `request` — there is no offline-buffering
vocabulary (`sendOrQueue` / `sendOrFail` / send policy), since a host plugin has nothing to buffer
across. Launch background work on `pluginScope` (also a property): the runtime cancels it when the
instance is disposed, so nothing can outlive the plugin. State that must survive across sessions does
**not** belong in the instance.

**On the agent** the `messenger` property is **connection-independent** (it outlives any single
connection), so app code may send at any time and choose the offline behavior per call: `trySend`
drops, `sendOrQueue` buffers, `sendOrFail` throws. Buffering is opt-in — override
`offlineEventBufferCapacity`, which defaults to **`0`** (so `sendOrQueue` behaves like `trySend`
until you raise it) and drops the oldest entry once full.

Two rules the buffer does not bend: **requests are never buffered** (`request` throws
`JetWhaleConnectionClosedException` while disconnected, whatever the capacity), and ordering is only
guaranteed *within* a policy — a `trySend` issued while queued events are still draining can overtake
them. Pick one policy per logical stream.

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
deadlock until the timeout. `onPrepare` is bounded by `prepareTimeoutMillis` (a `protected open val`
on both sides, **10 000 ms** by default) — on timeout (or failure) the runtime logs a warning
(visible in the host log) and opens handler dispatch anyway, so the plugin proceeds degraded rather
than hanging.

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

## Exposing MCP tools <Badge type="warning" text="experimental" />

A host plugin can contribute tools to the host's [MCP server](/guide/mcp-server) by implementing
`JetWhaleMcpCapablePlugin`. Each tool is a `JetWhaleMcpCommand`: a self-contained class holding the
tool's name, description, parameter schema, and execution logic. Parameters are declared as
delegated properties — the property name becomes the parameter name shown to the AI agent, and the
same property reads the value back, so each parameter has exactly one, compile-time-checked
definition:

```kotlin
@OptIn(ExperimentalJetWhaleApi::class)
class InspectWidgetCommand(private val widgets: WidgetStore) : JetWhaleMcpCommand() {
    override val name = "com.example.myplugin.inspectWidget"
    override val description = "Inspect the selected widget"

    private val widgetId by string("The widget ID")
    private val verbose by booleanOrNull("Include layout details.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        return widgets.describeAsJson(id = arguments[widgetId], verbose = arguments[verbose] ?: false)
    }
}

class MyPlugin : JetWhaleMessagingHostPlugin(), JetWhaleMcpCapablePlugin {
    override val mcpCommands = listOf(InspectWidgetCommand(widgets))
}
```

Things to know:

- **Names must be globally unique** — prefix them with your `pluginId` by convention.
- **`sessionId` is injected for you.** JetWhale adds a required `sessionId` parameter to every
  plugin tool's schema and routes the call to the right plugin instance, so your command runs
  against the correct session without handling it yourself.
- **`execute` returns a string** (plain text or JSON). Throw `JetWhaleMcpArgumentException` for
  caller mistakes — it is rendered as an `{"error": ...}` payload instead of failing the server.
- **Messaging works from tool handlers.** `messenger` is valid for the whole instance lifetime, so
  a command can `request` the agent directly.
- **Declare parameters as properties, never inside `execute`.** The list is read once, and declaring
  one afterwards — or declaring the same name twice — throws with a message saying so.
- **`mcpCommands` is read once** per plugin instance activation and treated as static from then on.
- Every declarator takes an optional `name` to override the wire name, for when the property cannot
  be called what the parameter should be called (`by stringOrNull("…", name = "name")`).
- The MCP APIs are marked `@ExperimentalJetWhaleApi` and may change between releases.

### Structured parameters

Beyond scalars (`string`, `int`, `long`, `boolean`, `enum`), a parameter can take structured input.
When the shape is known, declare it as a `@Serializable` type with `serializable<T>()`:

```kotlin
private val rules by serializable<List<MockRule>>("The mock rules to apply.")
```

The argument is decoded for you, and the parameter's JSON Schema is derived from the type's
serializer — nested objects, enum entries, and which properties are required (those without a
default value) are all advertised to the AI agent. You never have to restate the shape in the tool's
description, so it cannot drift from the model. A payload that doesn't fit the type raises a
`JetWhaleMcpArgumentException` naming the parameter.

Document individual properties with `@McpDescription`; the text lands in the schema next to the
field it describes. It reaches your classpath transitively through the host SDK, so there is no extra
dependency to declare:

```kotlin
@Serializable
data class MockMatcher(
    @McpDescription("HTTP method to match (case-insensitive). Matches any method if omitted.")
    val method: String? = null,
    @McpDescription("URL pattern to match, interpreted per matchType.")
    val urlPattern: String,
)
```

`@McpDescription` also applies to a whole `@Serializable` **class**, where it becomes that object's
schema description; a property's own annotation wins over the one inherited from its type. It is
deliberately not usable on a value parameter's use-site target — writing it on a constructor
parameter without a target is what makes it survive into the serial descriptor.

A sealed hierarchy is advertised as a `oneOf` over its subclasses, each pinning the class
discriminator (`"type"` by default, or whatever `@JsonClassDiscriminator` sets) to a `const` — the
same flattened shape `Json` reads and writes, so the agent can construct any variant from the schema
alone. Open polymorphic types have no statically known subclasses and fall back to an unconstrained
`object`.

`stringList` and `stringMap` cover the common flat containers. For payloads whose shape is not known
ahead of time, `jsonObject` and `jsonArray` hand back the raw `JsonElement` — but they can only
advertise `object` / `array`, so reach for `serializable` whenever the shape is known.

### Choosing the format

Arguments are decoded with `DefaultArgumentJson`, tuned for input written by an AI agent: unknown
keys are ignored, a scalar of the wrong JSON type is accepted where the value is unambiguous, enum
entries match case-insensitively, and trailing commas and comments are tolerated. (`coerceInputValues`
is deliberately off — it would swap an unrecognized enum entry for the property's default, turning a
caller mistake into a silently different result.)

Pass your own instance to the command's constructor when you need a `SerializersModule` (for
contextual or open polymorphic types), a different class discriminator, or a naming strategy:

```kotlin
class InspectWidgetCommand : JetWhaleMcpCommand(
    Json(from = DefaultArgumentJson) { serializersModule = widgetModule },
) { /* ... */ }
```

The schema is derived from the same instance, so the names and discriminator advertised to the
agent are the ones the command actually decodes. The format is also available to `execute` as the
protected `json` property, for encoding the result.

The Network Inspector's own tools (`com.kitakkun.jetwhale.network.*`) are a complete in-repo
example — see `jetwhale-plugins/network/host`.

### Hiding sensitive UI from MCP captures

`jetwhale.screenshot` renders the same Compose scene the host window shows, and
`jetwhale.getAccessibilityTree` reads that scene's semantics — which carry your `Text` and
`contentDescription` strings verbatim. If your plugin UI displays values that AI agents should not
see, read the `LocalIsMcpCapture` CompositionLocal — it is `true` only while the scene is
being rendered for either of those captures — and blank or mask the sensitive content while it is
raised. Masking only in the drawing layer is not enough: the semantics tree would still hand over
the original string. The interactive window never observes the raised state, so there is no
on-screen flicker.

The `LocalJetWhaleDarkTheme` CompositionLocal tells your plugin whether the host is rendering it in
a dark theme — the host provides the authoritative value from its actually-applied color scheme.
Read it (`LocalJetWhaleDarkTheme.current`) to pick theme-appropriate colors instead of
`isSystemInDarkTheme()`, which reflects the OS setting and can disagree with the host's own Theme
option.

### Theming: you already match the host

The host wraps your `Content()` in **its own** `MaterialTheme` before calling it, so plain
`MaterialTheme.colorScheme` / `MaterialTheme.typography` inside your plugin already resolve to the
host's applied scheme. Do not install a theme of your own unless you deliberately want to look
different — that is why `material3` is a `compileOnly` dependency and why plugins get visual
consistency for free.

The SDK ships **no component library** — no shared Composables, icons, scaffolds or spacing tokens.
Build your UI from Compose and Material 3 directly. Your composition is kept for the lifetime of the
plugin instance, so it survives switching to another plugin tab and back.

::: warning What the host SDK does *not* give a plugin
There is no settings/preferences API (render your own controls inside `Content()` and persist them
with [storage](#persistent-storage)), no logging API (use `println` or your own logging library —
the host's log viewer captures stdout/stderr), and no way to read the session's app/device metadata.
A plugin never learns its own session id; if you need facts about the app, `request` them from your
agent counterpart.

Members annotated `@InternalJetWhaleHostApi` (`bindMessenger`, `dispatchCreate`, …) show up in
autocomplete because the runtime calls them across module boundaries. Never call them — the opt-in is
an error, not a warning.
:::

## Persistent storage

Every host plugin instance gets a persistent key-value store via the protected `storage` property,
available from `onCreate()` onwards. Values live on disk under the host's app data directory and
survive plugin reloads, session changes and host restarts.

The store is **scoped to your `pluginId`**: a plugin can neither name another plugin's id nor reach
its data.

Anything with a `kotlinx.serialization` serializer can be stored — primitives, collections, and your
own `@Serializable` classes. The reified overloads resolve the serializer for you:

```kotlin
class MyPlugin : JetWhaleMessagingHostPlugin() {
    override fun onCreate() {
        pluginScope.launch {
            val filter = storage.get<String>("filter") ?: "all"
            storage.put("last-opened", System.currentTimeMillis())
        }
    }
}
```

The API is suspend/`Flow` based: `put` / `get` / `getFlow` / `contains` / `remove` / `clear`, plus
`keysFlow` to observe the stored key set. Each has an explicit-`KSerializer` overload alongside the
reified one, for types whose serializer you resolve yourself.

The key `__jetwhale_storage_version` is **reserved** — writing it throws, and it never shows up in
`keysFlow` or `contains`. `clear()` wipes the store and re-stamps the current `storageVersion`, so a
cleared store is not mistaken for legacy data by the migration below.

### Compose helper: `rememberPersistent`

Inside a plugin's UI, `rememberPersistent(key, default)` behaves like `rememberSaveable`, but backed
by the plugin's persistent store — the value survives host restarts:

```kotlin
@Composable
fun DraftField() {
    var draft by rememberPersistent("draft-input", default = "")
    OutlinedTextField(value = draft, onValueChange = { draft = it })
}
```

Two behaviours to design around: the state **starts at `default`** and is replaced asynchronously
once the stored value has been read, so the first frame shows the default; and writes are **debounced
by 300 ms**, so a value typed and immediately followed by a crash may not have reached disk. There is
also an overload taking an explicit `KSerializer`.

From UI code that has no plugin reference, `LocalJetWhalePluginStorage.current` gives the same
`pluginId`-scoped store as the `storage` property (it is `null` outside a host-managed scene).

### Migrating stored data

When the shape of your stored data changes, bump `storageVersion` and override
`onStorageMigrate(fromVersion)`. The runtime persists the version alongside the data and runs the
hook **once**, before the first storage operation completes after an update — reads never observe
pre-migration data:

```kotlin
class MyPlugin : JetWhaleMessagingHostPlugin() {
    override val storageVersion: Int = 2

    override suspend fun onStorageMigrate(fromVersion: Int) {
        if (fromVersion < 2) {
            // v1 stored the draft under "draft"; v2 renamed it.
            storage.get<String>("draft")?.let {
                storage.put("draft-input", it)
                storage.remove("draft")
            }
        }
    }
}
```

Rules of thumb:

- Stores written before versioning existed are treated as version `1`.
- A store written by a **newer** plugin version than the running one is left untouched; values that
  no longer decode simply read as `null`.
- A value that fails to decode (for example after a schema change without a migration) is treated as
  absent rather than crashing your plugin — but prefer writing a migration so the data is not lost.

## Hot reload (the live dev loop)

`runJetWhale` starts the host with `-Djetwhale.devPluginsDir=<dir>` pointing at a dev
directory under your module's `build` folder. The host loads plugins from that directory and watches
it: whenever the plugin jar is re-staged, the host reloads it and refreshes the open plugin screen —
**no host restart needed**. For simple edits it redefines your classes in place and keeps the plugin's
state; for changes it can't apply that way it recreates the plugin from a fresh classloader (see
[Limitations](#limitations) below).

### Isolated sandbox environment

`runJetWhale`, `runJetWhaleHot` and the in-repo `runJetWhaleLocal` run the host against an
**isolated, per-project sandbox** at
`build/jetwhale-sandbox/` instead of your real `~/.jetwhale/`. Everything the host persists — installed
plugins (`plugins/`, with Maven-installed dependencies under `plugins/libs/`), settings, per-plugin
data (`plugin-data/`), TLS material (`ssl/`) and the plugin trust registry
(`trusted-plugins.json`) — lives there, so trying a plugin never reads or mutates your actual JetWhale
installation. The sandbox starts empty: only your dev plugin is loaded (dev-directory plugins are
trusted implicitly, so there is no trust prompt to click through), and there are no leftover plugins
or settings from previous work.

Two system properties do this, both set for you by the launch tasks: `jetwhale.appDataDir` redirects
the app data root, and `jetwhale.devPluginsDir` names the watched staging directory. Note that
`installPlugin` is **not** affected — it deliberately targets your real `~/.jetwhale/plugins/`.

The sandbox lives under `build/`, so it **persists across re-launches** of the same project — test data
you set up survives the next `runJetWhale`. Running `./gradlew :myPlugin:clean` wipes it for a
completely fresh environment.

Non-dev launches (the installed JetWhale app) are unaffected: without the override they use
`~/.jetwhale/` exactly as before.

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
OS/architecture from the GitHub release — no manual install of JetWhale needed. Pass
`-PjetwhaleHostJar=<path>` to launch a locally built host uber jar instead, which skips the download
entirely.

The download is cached under `~/.jetwhale/dev-host/<version>/` and reused. A released version is only
fetched once; a `-SNAPSHOT` `hostVersion` is re-checked against the release asset's ETag on every
launch, so you pick up a newer snapshot without clearing anything by hand. The supported matrix is
macOS / Linux / Windows on `arm64` or `x64`; anything else fails with an explicit
*Unsupported OS/architecture* message.

### Testing against a real session

A plugin screen has nothing to render without a connected debuggee. `runJetWhaleQaAgent` starts a
headless one and gives you an HTTP control API to push messages at your plugin — see
[QA Agent](/guide/qa-agent).

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

## Publishing your plugin to Maven

Users can install a plugin straight from a Maven repository: **Settings → Plugins → Install from
Maven**, entering `group:artifact:version` (append `@https://your.repo/url` for a repository other
than Maven Central — pasting a Gradle dependency line or a Maven `<dependency>` block also works).

To make your plugin installable that way, apply a Maven publishing plugin (e.g.
[`com.vanniktech.maven.publish`](https://github.com/vanniktech/gradle-maven-publish-plugin) or plain
`maven-publish`) to the plugin module alongside `com.kitakkun.jetwhale.host`, and publish to any
Maven repository you like. The JetWhale plugin automatically makes the published main artifact the
`packageMavenPlugin` jar, which contains:

- your module's classes and resources, and
- `META-INF/jetwhale/dependencies.txt` — the flat `group:artifact:version` list of your runtime
  dependencies, exactly as Gradle resolved them at build time.

Dependencies are deliberately **not** bundled: the host downloads each jar listed in the manifest
when the plugin is installed (from your plugin's repository, falling back to Maven Central) and
puts them on the plugin's classpath. This keeps the published artifact small and
platform-independent, and means you never redistribute third-party code.

In-build project dependencies (e.g. a sibling `protocol` module) are listed in the manifest by
their own publication coordinates when the module has a Maven publication configured — publish
them alongside the plugin. A project dependency without a publication is bundled into the plugin
jar as a fallback.

Remember that host-provided dependencies (`jetwhale-host-sdk`, Compose, `material3`) must stay
`compileOnly` — they are provided by the host at runtime and must appear neither in the jar nor in
the dependency manifest.

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
