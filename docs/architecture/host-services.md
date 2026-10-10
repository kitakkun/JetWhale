# Host services: what the host hands to plugins

Status: **proposed**. Nothing here is built. It builds on the host session (#309) and redoes, in a
different shape, the plugin context that #244 proposed and #376 started.

## Goal

Several features need something that belongs to the host rather than to one plugin. Today each
plugin gets it on its own, or cannot get it at all:

- **Finding external tools.** The host finds adb for port mapping, and Device Mirror finds adb,
  ffmpeg, xcrun and iproxy with a copy of the same search. When the search fails, neither lets the
  user say where the tool is.
- **Processes per device.** The XCTest runner (#438) is shared between plugins only through files, a
  lock and an idle stop. Its start, its cleanup at exit and its signing team live in whichever plugin
  created the client. The iPhone capture helper (#443) is a second process per device.
- **Running without an agent.** The Compose Semantics Inspector's XCTest node source, phase 2 of
  [iOS input through an XCTest runner](./ios-xctest-runner.md), has to run in the host session. A
  plugin that talks to an agent cannot run there today.

This design gives the host one way to hand plugins what it owns:

- A plugin instance looks up a **host service** by its type, and gets null when the host has none.
- A plugin can declare that it also runs in the **host session**, and an instance knows which kind
  of session it runs in.
- The host implements the services: tool lookup with paths the user sets, the XCTest runners with
  their signing team, and later the device list.
- The settings behind the services are host settings, on host Settings pages, built from the host's
  own Repository, Service and soil-key layers.
- Plugins built against an older SDK keep loading unchanged. A plugin built against a newer SDK finds
  a service missing on an older host instead of failing.

Not in this design: services that one plugin provides to another (see
[Who implements a service](#who-implements-a-service)), an API for a plugin's own settings, and
changes to the agent protocol.

**Words.** A *host service* is an object the host hands to plugin instances through the SDK. It is
not a Service class of the host's own layers (`agents/rules/jetwhale-host-architecture.md`), though
one usually backs it. The design avoids the word "capabilities": it already names the agent's
negotiated `Capabilities`
(`jetwhale-protocol/core/src/commonMain/kotlin/com/kitakkun/jetwhale/protocol/negotiation/JetWhaleAgentNegotiationRequest.kt:77`)
and the launcher's `LauncherCapabilities`
(`jetwhale-host/release-metadata/src/main/kotlin/com/kitakkun/jetwhale/host/release/LauncherCapabilities.kt:12`).

## What exists today

Paths are on `main` unless a PR is named. Open PRs are cited at their head commits at the time of
writing: #438 at `386fdbd`, #443 at `f21178e`, #376 at `d94c6fe`. Below, `host/core/data/` stands
for `jetwhale-host/core/data/src/main/kotlin/com/kitakkun/jetwhale/host/data/`, and `sdk/` for
`jetwhale-host-sdk/src/main/kotlin/com/kitakkun/jetwhale/host/sdk/`.

### What a plugin instance is given

| What | How the plugin reaches it | Bound by |
|---|---|---|
| `pluginScope` | protected property, from `onCreate` | `bindPluginScope` (`sdk/JetWhaleHostPlugin.kt:76-80`) |
| `storage` | protected property, scoped to the plugin id | `bindStorage` (`sdk/JetWhaleHostPlugin.kt:82-86`) |
| `messenger` | protected property, messaging plugins only | `bindMessenger` (`sdk/JetWhaleMessagingHostPlugin.kt:69-73`) |

The host binds all three in `DefaultPluginInstanceService.createInstance`
(`host/core/data/plugin/DefaultPluginInstanceService.kt:140-175`) before it calls `onCreate`. The
factory takes nothing: `createPlugin()` (`sdk/JetWhaleHostPluginFactory.kt:3-9`). The plugin guide
says there is no settings API, and that "a plugin never learns its own session id"
(`docs/guide/developing-plugins.md:471-476`).

### Sessions and `requiresAgent`

- With `"requiresAgent": true`, the default (`sdk/JetWhaleHostPluginManifest.kt:41`), a plugin gets
  one instance per app session whose agent advertised it. With `false` it gets one instance in the
  host session `host` (`jetwhale-host/core/model/src/main/kotlin/com/kitakkun/jetwhale/host/model/HostSession.kt:10-16`),
  since #309. The rule lives in `targetSessionIds`
  (`host/core/data/plugin/DefaultPluginSessionReconciliationService.kt:33-40`).
- There is no third case. A messaging plugin with `"requiresAgent": false` gets a peer whose frames
  go to the session `host`, which no agent joins, and the host only logs a warning
  (`DefaultPluginInstanceService.kt:142-147`). The Compose Semantics Inspector is a
  `JetWhaleMessagingHostPlugin` that captures and acts only through `messenger.request`
  (`jetwhale-plugins/semantics/host/src/main/kotlin/com/kitakkun/jetwhale/plugins/semantics/host/ComposeSemanticsInspectorPluginFactory.kt:37-41`, `:118-132`),
  so it cannot have an instance in the host session.
- The manifest's KDoc and JSON schema still describe the behavior from before #309, "instantiated for
  every active session" (`sdk/JetWhaleHostPluginManifest.kt:32`,
  `schemas/plugin-manifest.schema.json:37`).
- The host decodes manifests with `ignoreUnknownKeys`
  (`host/core/data/plugin/JetWhaleHostPluginManifestFile.kt:51`), so an older host skips a key it
  does not know.

### Class loaders

Each plugin jar gets a `URLClassLoader` whose parent is the host's own loader
(`host/core/data/plugin/DefaultPluginFactoryRepository.kt:105-107`). Delegation is parent-first, so
the classes the host has (the SDK, `jetwhale-host-ui`, Compose) are shared by every plugin, and a
plugin's own copy of such a class is never loaded. Plugin jars do not see each other: two plugins
that bundle the same library each load their own copy of its classes, and an object from one cannot
be passed to the other.

### Tool lookup, twice

- **The host.** `AdbLocator` (`host/core/data/util/AdbLocator.kt:26-79`) searches `ANDROID_HOME`,
  `ANDROID_SDK_ROOT`, the SDK's default locations, the login shell's PATH, the process's own PATH and
  a few fixed directories. One instance per run (`:81-86`) serves port mapping, which resolves adb
  once per run (`host/core/data/server/DefaultAdbAutoPortMappingService.kt:40`), and Settings →
  Connection → ADB, which shows where adb was found (`host/core/data/settings/DefaultDiagnosticsQueryKey.kt:17-30`).
- **Device Mirror.** `MirrorToolLocator`
  (`jetwhale-plugins/mirror/host/src/main/kotlin/com/kitakkun/jetwhale/plugins/mirror/host/Shell.kt:116-130`)
  searches directories that Mirror gathers with its own copy of `LoginShellPathVariableResolver`
  (`MirrorHostPluginFactory.kt:36-56`). The host's copy says why there are two: "a plugin sees only
  the published host SDK" (`host/core/data/util/LoginShellPathVariableResolver.kt:17-18`). When it
  could read the login shell's PATH, Mirror also hands the directories it searched to the processes
  it starts as their PATH (`Shell.kt:20-35`, `MirrorHostPluginFactory.kt:44`), so that idb finds
  idb_companion and pyenv shims find what they stand for.
- **#435 (open)** reorders both searches: the Android SDK (adb only), the process's PATH, well-known
  install directories, and the login shell's PATH only when a tool is still missing. It leaves "a
  per-tool explicit path, as the way out when nothing is found" for a follow-up. Until then the
  guides tell users to link a tool into `~/.local/bin`.
- Mirror also works out the host's app data directory by itself
  (`jetwhale-plugins/mirror/host/src/main/kotlin/com/kitakkun/jetwhale/plugins/mirror/host/CaptureLibrary.kt:139-142`),
  repeating `host/core/data/AppDataDirectoryProvider.kt:23-30`.

### The XCTest runner and the iPhone capture helper (open PRs)

- **#438** adds `jetwhale-plugins/ios-xctest-runner/`. Its client API is narrow: `refusalFor`,
  `runnerFor`, `startRunnerInBackground`, and a factory `XcTestRunners.onThisMac(stateDirectory,
  xcrunPath, iproxyPath, settings)`
  (`jetwhale-plugins/ios-xctest-runner/client/src/main/kotlin/com/kitakkun/jetwhale/plugins/xctestrunner/XcTestRunners.kt:21-63`).
  Plugins share runners through `<app data>/xctest-runner/runners/<udid>.json` and a lock beside it
  ([Shared through the OS](./ios-xctest-runner.md#shared-through-the-os)).
- Mirror creates one `XcTestRunners` per class loader, in top-level properties (#438's
  `MirrorHostPluginFactory.kt:100-109`). It keeps the signing team in a process-wide
  `RunnerSigningTeam`, stored in Mirror's own plugin storage (#438's `DevelopmentTeamSetting.kt:15-58`),
  finds iproxy with its own search, and reads the app data directory from the system property
  (`:185`). Every `onThisMac` call registers a JVM shutdown hook (#438's `LocalXcTestRunners.kt:314`).
- **#439** keeps a runner alive with a lease in its state file, through a Mirror MCP tool
  `keepRunnerAlive`. **#442** lists simulators and iPhones with `simctl` and `devicectl` inside
  Mirror. **#443** adds a Swift capture helper per iPhone, built under Mirror's plugin data directory
  (#443's `MirrorHostPluginFactory.kt:84-102`). On an iPhone the capture must start before the
  runner, because starting it drops iproxy's connection (#443's `IosPhysicalDeviceController.kt:22-24`).

Once a second plugin uses the runner, this costs:

- **Two signing teams.** A client replaces a device's runner that was "signed for another team"
  ([Shared through the OS](./ios-xctest-runner.md#shared-through-the-os)), so two plugins with
  different teams keep replacing each other's runner.
- **One exit hook per class loader.** A hot reload runs those top-level initializers again in a new
  class loader, and the hook registered from the old one keeps it reachable.
- **Order on an iPhone.** A plugin that starts the runner without starting the capture first has its
  iproxy connection dropped when Mirror later starts the capture.

### Earlier plugin contexts

- **#244 (closed)** proposed host-scoped plugins and a `JetWhaleHostPluginContext` with `adb` and
  `sessions`, passed to `createPlugin(context)`. Its closing comment: the host-scoped part landed as
  the host session (#309), and the context is to be redone small on top of `main`.
- **#376 (open, stacked on #375)** changes `createPlugin()` into `createPlugin(context)` with an
  experimental `context.adb` (`JetWhaleAdb`: a timeout on every call, stdout and stderr kept apart).
  Every plugin factory changes. #245 (open, the Android Device plugin) builds on it.

### MCP

- A plugin tool is registered per session: the registry maps a tool name to the sessions that offer
  it, and keeps the descriptor of the first registration
  (`jetwhale-host/core/mcp/src/main/kotlin/com/kitakkun/jetwhale/host/mcp/McpToolRegistry.kt:48-56`).
  Every plugin tool gets a `sessionId` parameter.
- `jetwhale.listSessions` lists `host` first, with the plugins that need no app
  (`jetwhale-host/core/mcp/src/main/kotlin/com/kitakkun/jetwhale/host/mcp/tools/SessionTools.kt:35-48`, `:87-88`).
- Host-wide tools read and change host state: `jetwhale.getStatus`, `jetwhale.updateSettings`
  (`jetwhale-host/core/mcp/src/main/kotlin/com/kitakkun/jetwhale/host/mcp/tools/host/HostSettingsCommands.kt:34-58`),
  and `jetwhale.navigate`, which opens any Settings page
  (`jetwhale-host/core/model/src/main/kotlin/com/kitakkun/jetwhale/host/model/HostNavigationService.kt:37-49`).

## Options

### How a plugin reaches host services

| | Factory parameter (#376) | Bound to the instance, like `storage` | A static accessor in the SDK |
|---|---|---|---|
| Plugins built against the current SDK | Break: every factory changes. A default `createPlugin(context) = createPlugin()` would bridge old factories, since Kotlin compiles interface bodies to JVM default methods from language version 2.2 ([`JvmCompilerArguments.kt:182-189` at v2.4.10](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/arguments/src/org/jetbrains/kotlin/arguments/description/JvmCompilerArguments.kt#L182-L189)), but a factory written for the new overload would still have to implement the old abstract one | Unaffected: nothing they implement changes | Unaffected |
| Available | In the factory, before the instance exists | From `onCreate`, like `storage` | Anywhere, including code that belongs to no instance |
| Who asks | The plugin id of the factory | The plugin and its session: one object is bound per instance | Unknown |
| On a host older than the SDK | With #376's signature, the host calls `createPlugin()`, which the factory no longer has: the instance is not created | The accessor is inlined into the plugin and can turn the missing binding into null ([Versioning](#versioning-and-compatibility)) | The class is missing: the call fails |

Recommendation: bound to the instance.

### How a service is found

- **Members of one context interface** (`context.adb`, `context.toolLocator`): every service added
  later is a method that an older host's interface lacks, and calling it fails with
  `NoSuchMethodError`.
- **A lookup by type**, `find(type: Class<T>): T?`: one method, there from the first version. A
  service type added later is a class that an older host lacks, and naming it fails with
  `NoClassDefFoundError`.
- **The same lookup through an inline function**, compiled into the plugin, that turns exactly those
  two errors into null: `NoSuchMethodError` where the entry point is missing, and
  `NoClassDefFoundError` where the service type is. Chosen.

### Who implements a service

| | Implemented by the host | Provided by a plugin, the host as broker |
|---|---|---|
| Platform code | In the host: xcodebuild, iproxy, simctl, the runner's Xcode project | In the provider plugin |
| Types both sides use | From the host: the SDK, or an artifact the host provides like `jetwhale-host-ui` | The same: provider and consumer load in separate class loaders, so the interface must still come from the host. Only the implementation moves |
| Lifetime | The host process: created once, ended at exit, untouched by plugin reloads | The provider's instance: disabling or reloading the provider invalidates every consumer's reference, and consumers need a way to learn of it |
| Versions | One: the host and its services ship together | Provider and consumer differ, and each pair needs a version range like `agentVersionRange` |
| Order | Always there | A consumer can start before its provider, or while the provider is disabled |
| Third parties | Use the services; cannot add one | Can add services, and then see calls from other plugins, which the trust prompt never covered |
| Shipping a fix | A host release | A provider release |

Recommendation: implemented by the host. The official plugins ship with the host at one version
anyway, and the host already carries platform code (port mapping, the login shell). A provider plugin
would still need its types in the host, and would add problems of order, lifetime and versions that
no consumer needs solved yet. Revisit when a third party needs to provide a service.

### Where a service's types live

- **In `jetwhale-host-sdk`**: one contract, one ABI dump. The SDK grows types that only macOS uses.
- **In a separate artifact the host provides**, like `jetwhale-host-ui`, which plugins depend on
  `compileOnly`. The XCTest runner client already is its own artifact, `jetwhale-ios-xctest-runner`,
  with its own ABI dump (#438's `client/api/client.api`), and
  [The client's API](./ios-xctest-runner.md#the-client-s-api) promises its callers that nothing
  changes if the host takes it over.

Recommendation: services every platform has (tools, adb, the plugin's directory, later the device
list) in the SDK; the iOS runner's types stay in `jetwhale-ios-xctest-runner`, which the host then
provides.

### How a plugin runs without an agent

- **A separate host-only plugin** for the agentless case: a second sidebar entry, a second set of MCP
  tools under other names, and the UI shared through a library. The runner's design planned phase 2
  "through the existing semantics MCP tools".
- **`"requiresAgent": false` on a messaging plugin meaning "the host session too"**: an older host
  reads it as host-only, and runs the plugin only where its messenger reaches no agent.
- **A new manifest key** that adds a host-session instance to a plugin that otherwise needs an agent.
  An older host ignores the key and runs the plugin as it does today. Chosen.

## Proposed design

Each plugin instance gets a services object and a session flag, both bound by the host like its
storage. The host implements every service on top of its own Services and Repositories, and keeps
their settings on its own Settings pages.

```
plugin instance, in any session
  isInHostSession          bound per instance
  findHostService<T>() ──▶ JetWhaleHostServices, bound per instance
    ├─ JetWhaleToolLocator      ◀─ ToolLocatorService ◀─ UserToolPathsRepository
    ├─ JetWhalePluginDirectory  ◀─ AppDataDirectoryProvider
    ├─ JetWhaleAdb (#376)       ◀─ ToolLocatorService
    ├─ XcTestRunners, XcTestRunnerTargetListing, XcTestRunnerSigning   (macOS)
    │                           ◀─ XcTestRunnerService ◀─ IosSigningRepository
    └─ JetWhaleDevices (later)  ◀─ DeviceListService

host Settings → Connection → Tools, iOS devices: soil keys over the same Repositories
MCP: jetwhale.getStatus (read), jetwhale.updateSettings (team), jetwhale.keepXcTestRunnerAlive
```

### Session scope

A new manifest key, `"runsInHostSession"`, default `false`:

| `requiresAgent` | `runsInHostSession` | Instances |
|---|---|---|
| `true` (default) | `false` (default) | One per app session whose agent advertised the plugin, as today |
| `false` | ignored | One in the host session, as today |
| `true` | `true` | One in the host session, and one per app session whose agent advertised the plugin |

For the third row:

- **Reconciliation.** The target sessions are the advertising app sessions plus `host`
  (`targetSessionIds`, `DefaultPluginSessionReconciliationService.kt:33-40`). The host-session
  instance exists while the plugin is enabled, with or without an app. `Activated` carries only app
  session ids: it is forwarded to each session's agent
  (`host/core/data/server/DefaultDebugWebSocketServer.kt:118-127`), and the host session has none.
- **No messaging in the host session.** A messaging plugin's host-session instance gets no peer:
  `configure` is not called, `onPrepare` does not run, and `messenger` throws an
  `IllegalStateException` that says the host session has no agent. App sessions are unchanged.
- **The instance knows.** `isInHostSession` is true in the host session and false in app sessions.
  It is not the session id, so the guide's promise stands.
- **Storage** is per plugin id and shared by both kinds of instance
  (`host/core/data/plugin/DefaultPluginDataStoreRepository.kt:39-45`).
- **MCP.** The host-session instance's tools answer at `sessionId: "host"`. The registry keeps the
  first descriptor of each tool name, so both kinds of instance return the same commands with the
  same parameters. A parameter only one kind uses is optional, and the other refuses it.
- **Sidebar.** Two entries: one in the host section above the app picker, with the host-only plugins,
  and one among the selected app's plugins when that app advertises it. Each opens its own instance.
  The drawer picks a plugin's session from `needsApp` today
  (`jetwhale-host/app/src/main/kotlin/com/kitakkun/jetwhale/host/drawer/ToolingScaffoldUiState.kt:76-79`),
  and keys and selects its rows by plugin id alone, so a row is identified by its plugin and its
  session instead.
- **Host tools.** `jetwhale.navigate` takes `host` or an app's id for such a plugin; without one it
  opens the selected app's instance when there is one, and the host session's otherwise.
  `listSessions` and `listPlugins` list it under `host`, and `listInstalledPlugins` reports
  `runsInHostSession`.
- **Unchanged:** stopping the server keeps host-session instances
  (`DefaultPluginInstanceService.kt:246-248`), and a reload replaces every instance of the plugin,
  the host session's included (`:100-103`).

### Host services

- **One object per instance.** The host binds a `JetWhaleHostServices` to each instance before
  `onCreate`, next to its storage. It knows the plugin and the session, so a service can give each
  plugin its own directory, and can tell which plugin started a runner.
- **Null means absent.** `findHostService<T>()` returns null when the host is older than `T`, or
  `T` does not run on this OS (the iOS services on Linux and Windows). A service that exists but
  cannot work right now (no Xcode, no team) is not null: it says why through its own API, as
  `XcTestRunners.refusalFor` does.
- **Services outlive instances.** A plugin may hold a service for the life of its instance. A service
  never keeps a plugin's object after that: what a plugin passes in is a value, or is used during the
  call, and a plugin collects a service's flows in its `pluginScope`. Otherwise a service would keep
  a disposed plugin's class loader alive.
- **Suspend where it can wait.** Anything that can wait on the login shell, xcodebuild or a device
  is a `suspend` function, so no plugin blocks its UI thread on it.

### Which services, in which order

1. **Session scope.** Not a service, but what phase 2 needs first. Specified above.
2. **The tool locator, with paths the user sets.** This absorbs #435's follow-up and removes the
   duplicated search.
   - `JetWhaleToolLocator.findExecutable(toolName)` and `childProcessPathVariable()`.
   - #435's order, with the user's path first. A user path that is not an executable file gives null,
     and the Tools page says why, rather than a quiet fallback to the search that would hide a typo.
   - The Tools page lists the host's adb, every tool a loaded plugin declares in its manifest
     (`"tools": ["ffmpeg", "xcrun", "iproxy"]`), and every tool with a saved path.
   - In the same step, two small services: `JetWhalePluginDirectory`, the plugin's own directory
     under the app data, which replaces the hard-coded `plugin-data/com.kitakkun.jetwhale.mirror`;
     and #376's `JetWhaleAdb`, which runs the adb the locator finds.
3. **The iOS runners in the host** (macOS).
   - The host creates `XcTestRunners` once per process: its state directory from
     `AppDataDirectoryProvider`, xcrun and iproxy from the locator, the team from a host Repository.
     One exit hook per process, registered by the host.
   - Plugins get `XcTestRunners`, a listing of the simulators and iPhones a runner can drive (#442's
     `simctl` and `devicectl` parsing moves here), and the signing team to read and change.
   - The sharing through the OS stays (state file, lock, idle stop), so two host processes that use
     the same app data directory still meet there. A development host on its sandbox directory does
     not meet the installed app; see Risks.
   - #439's lease becomes a host tool.
   - Phase 2 lands after this step, on simulators.
4. **The iPhone capture joins the iOS service.** The capture has to start before the runner on an
   iPhone, and only one owner of both can keep that order. Mirror's helper and its builds (#443) move
   into the host, and Mirror reads frames through the service. Until then, the Semantics XCTest
   source drives simulators only.
5. **The device list for every platform**, `JetWhaleDevices`: Android from the host's adb, iOS from
   step 3. The host tracks Android devices today only while port mapping is on
   (`DefaultDebugWebSocketServer.kt:81-82`), so the list gets its own tracking. Its users: Mirror's
   device discovery, #245's choice of a serial, and later the Semantics device picker.
6. **Services provided by plugins**: later, if ever ([Who implements a service](#who-implements-a-service)).

### Settings

The settings behind a service are host settings. No plugin draws them.

- **Layers.** One Repository per store, each with a DataStore file of its own; a Service that
  applies the settings; soil keys for the screens, one interface per key in `core/model` and a
  `Default…` binding in `core/data`, as `AdbAutoPortMappingMutationKey`
  (`jetwhale-host/core/model/src/main/kotlin/com/kitakkun/jetwhale/host/model/AdbAutoPortMappingMutationKey.kt:5`)
  and `DefaultAdbAutoPortMappingMutationKey`
  (`host/core/data/server/DefaultAdbAutoPortMappingMutationKey.kt:13-20`) do; a page in `SettingsScreenPage`
  (`jetwhale-host/feature/settings/src/main/kotlin/com/kitakkun/jetwhale/host/settings/SettingsScreenMenu.kt:56-87`),
  and the same page in `HostSettingsPage` so that `jetwhale.navigate` opens it.
- **Settings → Connection → Tools.** One row per tool: the path found, or "not found"; where it was
  found (the user's path, the Android SDK, PATH, a well-known directory, the login shell); the user's
  path, with Browse and Clear; and a **Search again** button. The ADB page keeps port mapping and
  links here for adb's path.
- **Settings → Connection → iOS devices** (macOS). The development team; each device's runner, with
  its lease and **Stop**; the runner and helper builds, with their size and **Delete**.
- **How plugins surface them.** A plugin reads the state through the service and shows it where its
  user needs it: a missing ffmpeg as a notice in Mirror. What belongs to the plugin's own flow it
  changes through the service: Mirror's **Set team…** dialog writes the host's team, so the dialog
  and the Settings page edit one value. A button that opens a host Settings page needs a small
  navigation service; it is added when the first plugin needs one, likely Mirror's missing-tool
  notice.

### MCP

- **Services are not tools.** Plugins keep their tools and use the services behind them.
- **`jetwhale.getStatus`** reports each tool (path, where it was found, the user's path) and, on
  macOS, whether a team is set and each runner with its lease.
- **`jetwhale.updateSettings`** gains `iosDevelopmentTeam`. Tool paths cannot be set over MCP: the
  host runs whatever program the path names, so an agent could choose a program to run on the user's
  Mac. Only the Tools page sets a path.
- **`jetwhale.keepXcTestRunnerAlive`** replaces Mirror's `keepRunnerAlive` (#439): the lease belongs
  to the runner, which Semantics' host-session tools also use.
- **Host-session instances** of a plugin with `runsInHostSession` answer at `sessionId: "host"`.

### Versioning and compatibility

The SDK's ABI is checked by `checkKotlinAbi` (`explicitApi()` and `abiValidation()`,
`jetwhale-host-sdk/build.gradle.kts:13-18`). The design adds to it, and replaces one constructor:

- `JetWhaleHostPlugin` gains two bound properties, their binders and two inline accessors. The
  factory interface does not change.
- `JetWhaleHostPluginManifest` gains `runsInHostSession` and `tools`. Its primary constructor takes
  them as parameters, so the dump's current constructor is replaced rather than kept. Only the host
  and its tests construct a manifest; a plugin's manifest is a JSON file that the host decodes. The
  old constructor is therefore not kept as an overload.
- **Older plugins on a newer host** never call the new members, and run unchanged. A plugin class
  that happens to declare a member with the JVM name of a new final member fails to load: its
  instance is not created and the host logs why (`DefaultPluginInstanceService.kt:120-138`). The new
  names are chosen to make that unlikely.
- **Newer plugins on an older host.** The older host ignores `runsInHostSession` and `tools`, so the
  plugin gets no host-session instance and no tools are listed. `isInHostSession` and
  `findHostService` are inline, so their bodies are compiled into the plugin. On a host from before
  this design the bound property's getter does not exist, and its `NoSuchMethodError` gives `false`
  and null; a host-only plugin, which runs only in the host session anyway, must not rely on the flag
  there. On a host that predates one service, naming the service's type throws
  `NoClassDefFoundError`, which gives null. Only those two errors, at those two points, are caught:
  anything the host's own `find` throws reaches the plugin, so a broken host is not mistaken for a
  missing service. Any other SDK API added later still fails on an older host, as new API does
  today.
- **A released service interface never gains a member.** On an older host the interface itself, which
  the host provides, lacks the member, and the call fails with `NoSuchMethodError` in plugin code that
  the lookup no longer guards. A new member goes into a new interface that the same object also
  implements, found by its own type, so the lookup stays the only version check.
  While a service is `@ExperimentalJetWhaleApi`, it may change incompatibly, and its PR says so.
- **Artifacts the host provides besides the SDK** (`jetwhale-ios-xctest-runner` from step 3) follow
  the same rules, and plugins depend on them `compileOnly`. Since a plugin's class loader asks the
  host's loader first, a bundled copy is never loaded; a plugin built against another version gets
  the host's without notice.
- **A compatibility test** covers both directions: the previous release's official plugin jars load
  and run in the new host, and a fixture plugin built against the new SDK runs in the previous
  release's host, with null services and no host-session instance. Nothing tests this today:
  `compat-test/` checks the Kotlin versions that can consume the agent artifacts.

## API sketch

### SDK

```kotlin
public abstract class JetWhaleHostPlugin {
    // … pluginScope, storage, lifecycle as today

    @PublishedApi
    internal var boundIsInHostSession: Boolean? = null

    @PublishedApi
    internal var boundHostServices: JetWhaleHostServices? = null

    /**
     * Whether this instance runs in the host session, which has no app and no agent: always for a
     * plugin with `"requiresAgent": false`, and for the host-session instance of one with
     * `"runsInHostSession": true`. Available from [onCreate]. False on a host older than this SDK,
     * which cannot say; there a plugin with `"requiresAgent": false` still runs only in the host
     * session, and one that needs an agent never does.
     */
    protected inline val isInHostSession: Boolean
        get() {
            val bound = try {
                boundIsInHostSession
            } catch (_: NoSuchMethodError) {
                return false
            }
            return checkNotNull(bound) { "isInHostSession is only available in or after onCreate()." }
        }

    /**
     * The host's service of type [T], or null when this host has none: it is older than [T], or [T]
     * does not run on this OS. Available from [onCreate]; the service stays valid for the life of this
     * instance.
     */
    @ExperimentalJetWhaleApi
    protected inline fun <reified T : Any> findHostService(): T? {
        val services = try {
            boundHostServices
        } catch (_: NoSuchMethodError) {
            return null
        }
        val type = try {
            T::class.java
        } catch (_: NoClassDefFoundError) {
            return null
        }
        return checkNotNull(services) { "findHostService is only available in or after onCreate()." }.find(type)
    }

    @InternalJetWhaleHostApi
    public fun bindSessionKind(isInHostSession: Boolean) {
        boundIsInHostSession = isInHostSession
    }

    @InternalJetWhaleHostApi
    public fun bindHostServices(services: JetWhaleHostServices) {
        boundHostServices = services
    }
}

/** What the host provides to one plugin instance, reached through [JetWhaleHostPlugin.findHostService]. */
@ExperimentalJetWhaleApi
public interface JetWhaleHostServices {
    public fun <T : Any> find(type: Class<T>): T?
}

/**
 * Finds the programs a plugin runs the way the host finds its own adb, and honors the path the user
 * set for a program under Settings → Connection → Tools.
 */
@ExperimentalJetWhaleApi
public interface JetWhaleToolLocator {
    /**
     * The executable named [toolName], such as `adb`, `ffmpeg`, `xcrun` or `iproxy` (`.exe` is added on
     * Windows): the user's path for it when one is set, otherwise the first match of the host's search.
     * Null when the user's path is not an executable file, or when no path is set and the search finds
     * nothing. A call that has to read the login shell's PATH waits for it, at most once per run.
     */
    public suspend fun findExecutable(toolName: String): File?

    /**
     * The PATH for the processes a plugin starts: the directories the host searched, in order, so a
     * program that looks for another one on PATH, such as a pyenv shim, finds what the host found.
     */
    public suspend fun childProcessPathVariable(): String
}

/** A directory of the plugin's own under the host's app data, kept across runs and host updates. */
@ExperimentalJetWhaleApi
public interface JetWhalePluginDirectory {
    public val directory: File
}

// JetWhaleAdb and its result and exceptions: as in #376.
```

The manifest gains two optional keys, which older hosts ignore:

```json
{
  "pluginId": "com.example.myplugin",
  "pluginName": "My Plugin",
  "version": "1.0.0",
  "factoryClass": "com.example.MyPluginFactory",
  "runsInHostSession": true,
  "tools": ["ffmpeg"]
}
```

### iOS, in `jetwhale-ios-xctest-runner` as the host provides it

```kotlin
// XcTestRunners, XcTestRunner, XcTestRunnerTarget: as in #438 and #439. Phase 2 of the runner adds
// the element tree and the system alert (`/tree`, `/alert`) to XcTestRunner.

/** The simulators and USB iPhones on this Mac that a runner can drive. */
public interface XcTestRunnerTargetListing {
    /** Booted simulators and wired iPhones, listed again every few seconds while collected. */
    public fun targetsFlow(): Flow<List<ListedXcTestRunnerTarget>>
}

/**
 * One listed target and the name its device shows. Equal by value, written out as the SDK's published
 * value types are, so that `copy` and `componentN` do not join the ABI; [XcTestRunnerTarget] gets the same.
 */
public class ListedXcTestRunnerTarget(public val target: XcTestRunnerTarget, public val name: String) {
    override fun equals(other: Any?): Boolean = other is ListedXcTestRunnerTarget && target == other.target && name == other.name

    override fun hashCode(): Int = 31 * target.hashCode() + name.hashCode()

    override fun toString(): String = "ListedXcTestRunnerTarget(target=$target, name=$name)"
}

/** The Apple development team that signs the runners for iPhones; one per host. */
public interface XcTestRunnerSigning {
    public val developmentTeamFlow: StateFlow<String?>

    /** Sets the team, or forgets it when [team] is null; the iOS devices settings page shows the same value. */
    public suspend fun updateDevelopmentTeam(team: String?)
}
```

### Host

```kotlin
// core/model
interface UserToolPathsRepository {
    val userToolPathsFlow: Flow<Map<String, String>>
    suspend fun updateUserToolPath(toolName: String, path: String?)
}

interface ToolLocatorService {
    suspend fun locateTool(toolName: String): ToolLocation
    suspend fun searchedDirectories(): List<String>
}

data class ToolLocation(val toolName: String, val executable: File?, val foundBy: ToolSearchStep, val userPath: String?)

enum class ToolSearchStep { UserPath, AndroidSdk, ProcessPath, WellKnownDirectory, LoginShellPath, NotFound }

data class UserToolPathUpdate(val toolName: String, val path: String?)

typealias ToolLocationsQueryKey = QueryKey<List<ToolLocation>>
typealias UpdateUserToolPathMutationKey = MutationKey<Unit, UserToolPathUpdate>

// core/data
// DefaultToolLocatorService: AdbLocator's search for adb, #435's order, the login shell once per run.
// DefaultHostServices(pluginId, …) : JetWhaleHostServices, one per instance, bound in createInstance.
// XcTestRunnerService: XcTestRunners.onThisMac(...) once per process, on macOS only.
```

## Migration

### `AdbLocator`

- Its adb search (SDK roots, `adb.exe` on Windows) becomes the adb entry of
  `DefaultToolLocatorService`. `AdbLocatorProvider` (`AdbLocator.kt:81-86`) goes.
- `DefaultAdbAutoPortMappingService` (`:40`) and `DefaultDiagnosticsQueryKey` (`:23`) ask the
  locator for `adb`. Port mapping finds adb when it starts tracking rather than once per run, so a
  path the user sets applies at the next start of the debug server.
- `AdbLocatorTest`'s order cases move to the locator's tests. The Settings ADB page's "where adb was
  found" (`GeneralSettingsScreen.kt:521`) moves to the Tools page.

### `MirrorToolLocator`, and the second `LoginShellPathVariableResolver`

- Mirror deletes its lookup (`Shell.kt:103-145`) and its copy of `LoginShellPathVariableResolver`.
  The jar-wide `toolPaths` (`MirrorHostPluginFactory.kt:36-56`) becomes calls to
  `findHostService<JetWhaleToolLocator>()`,
  and `SystemProcessLauncher.launchedProcessPathVariable` (`Shell.kt:25-26`) comes from
  `childProcessPathVariable()`. xcrun comes from the locator rather than `/usr/bin/xcrun`
  (`Shell.kt:126`).
- Mirror's manifest declares its tools. `defaultCapturesRoot()` (`CaptureLibrary.kt:139-142`) uses
  `JetWhalePluginDirectory`.
- The guides' "How the tools are found" (`docs/guide/device-mirror.md`) and "How adb is found"
  (`docs/guide/adb-auto-port-mapping.md`) point at the Tools page rather than at linking into
  `~/.local/bin`.

### `XcTestRunners` and the signing team (#438)

- The host depends on the client and provides it. Mirror's `implementation` dependency on it
  (#438's `jetwhale-plugins/mirror/host/build.gradle.kts:43`) becomes `compileOnly`. The module moves
  out of `jetwhale-plugins/`, since the host depends on it.
- `XcTestRunnerService` calls `XcTestRunners.onThisMac` once per process, with
  `<app data>/xctest-runner` from `AppDataDirectoryProvider`, xcrun and iproxy from the locator, and
  an `XcTestRunnerSettings` backed by `IosSigningRepository`. `onThisMac` stays public for the host;
  its KDoc tells plugins to look the runners up instead.
- Mirror deletes `runnerSigningTeam`, `iproxyPath`, `xcTestRunners` and `appDataDirectory()` (#438's
  `MirrorHostPluginFactory.kt:100-109`, `:185`), and `RunnerSigningTeam` with `DevelopmentTeamSetting`.
  Its **Set team…** dialog calls `XcTestRunnerSigning.updateDevelopmentTeam`.

### The lease and the device listing (#439, #442)

- Mirror's `KeepRunnerAliveCommand` gives way to `jetwhale.keepXcTestRunnerAlive`. `listDevices`
  still reports `runnerKeptAliveUntil`, read through the service.
- The iOS half of Mirror's device listing uses `XcTestRunnerTargetListing`. The Android half stays
  in Mirror until step 5.

### The iPhone capture helper (#443)

Step 4 moves `IphoneScreenCaptures`, `IphoneCaptureHelperBuilds` and the Swift source into the
host's iOS service, which then starts an iPhone's capture before its runner whoever asks first. Mirror
reads the iPhone's H.264 through the service. The Camera usage description #443 adds to the launcher
already names the app that runs the host, so the permission does not move.

### #376 and #245

#376 keeps `JetWhaleAdb`, `DefaultJetWhaleAdb` and their tests, drops the change to
`createPlugin`, and registers `DefaultJetWhaleAdb` over the locator as a service. #245 keeps
`createPlugin()` and looks adb up with `findHostService<JetWhaleAdb>()`; a null service takes the
path it already has for a missing adb.

### Compose Semantics phase 2: the work list

**Host, step 1 (session scope):**

1. SDK: `runsInHostSession` on `JetWhaleHostPluginManifest` and in the JSON schema;
   `isInHostSession` and `bindSessionKind` on `JetWhaleHostPlugin`; the stale `requiresAgent` KDoc
   and schema text corrected. `JetWhaleMessagingHostPlugin`'s KDoc promises every instance a live
   counterpart, a `messenger` from `onCreate`, and `configure` and `onPrepare`
   (`sdk/JetWhaleMessagingHostPlugin.kt:6-18`, `:22-29`); it is reworded to hold for app sessions,
   and to say what a host-session instance gets instead.
2. `DefaultPluginSessionReconciliationService`: `targetSessionIds` adds `host`, and `Activated`
   leaves it out.
3. `DefaultPluginInstanceService.createInstance`: binds the session kind; creates no peer and runs
   no preparation in the host session.
4. `PluginMetaData` and `DefaultLoadedPluginMetaDataSubscriptionKey` carry the key, and the drawer
   lists the plugin in both places (`ToolingScaffoldPresenter.kt:137-161`). A drawer row is
   identified by its plugin id and session id together: the row keys
   (`ExpandedToolingDrawerView.kt:450`, `:505`, `ShrunkToolingDrawerView.kt:120`), the selected
   state (`ExpandedToolingDrawerView.kt:456`, `:516`, `ShrunkToolingDrawerView.kt:123`), and the
   click and pop-out callbacks, which pass the row's session rather than asking
   `sessionIdFor(pluginId)` (`ToolingScaffoldUiState.kt:76-79`).
5. MCP: `jetwhale.navigate` (`HostNavigationCommand.kt:112`, `:127-130`), `jetwhale.setPluginEnabled`
   (`HostPluginCommands.kt:105`), `listSessions` and `listPlugins` (`SessionTools.kt:87-88`),
   `listInstalledPlugins` (`HostPluginCommands.kt:56`).
6. Tests: reconciliation with a host-session instance, no peer in the host session, the two drawer
   rows of one plugin opening, selecting and popping out their own instances, and MCP routing to
   `host`.

**Host, step 3 (the iOS runners):** `XcTestRunners`, `XcTestRunnerTargetListing` and
`XcTestRunnerSigning` as services.

**Runner client:** `/tree` and `/alert`, and their Kotlin API on `XcTestRunner`, from the runner's
[commands table](./ios-xctest-runner.md#commands).

**Compose Semantics:**

1. Manifest: `"runsInHostSession": true`. It runs no tool itself; the runner service does.
2. A node source chosen in `onCreate` from `isInHostSession`: the agent through `messenger` in app
   sessions, or XCTest through the runners in the host session. Captures and actions go through it
   (`ComposeSemanticsInspectorPluginFactory.kt:118-132`).
3. The XCTest source maps a snapshot onto `AppleNode`s, one root per app (the foreground app and
   SpringBoard), and turns actions into gestures in `screen` points, `SetText` included, as
   [Compose Semantics (phase 2)](./ios-xctest-runner.md#compose-semantics-phase-2) specifies.
4. A device picker in the host-session instance, fed by `XcTestRunnerTargetListing`, with the choice
   kept in storage under a key of its own.
5. The host-session screen leaves out the view attribute panel and the node highlight. Both are
   built on `messenger` (`ViewAttributeStore` and `NodeHighlightController`,
   `ComposeSemanticsInspectorPluginFactory.kt:52-69`) and wired into the screen
   (`:75-115`), so the plugin creates them in app sessions only, and the screen takes them as
   optional.
6. MCP: every semantics tool gains an optional `deviceId`, a UDID. In the host session it is required
   when more than one device is listed; in app sessions it is refused. The view attribute and
   highlight tools refuse in the host session, which has no agent to answer them.
7. Without the runner service (another OS, no Xcode), the host-session instance says what it needs.
8. Simulators only until step 4; an iPhone is listed with the reason.
9. Tests: the XCTest mapping and gestures against a fake runner, and the host-session screen without
   the agent-only panels.

If step 3 is late, phase 2 can start on simulators right after step 1 with the client bundled, as
Mirror does in #438, and switch to the host's runners later: the interface is the same.

**Docs:** `docs/guide/developing-plugins.md` (the two keys, `isInHostSession`, `findHostService`,
and the warning that lists what the SDK does not give), `docs/guide/compose-semantics-inspector.md`,
`docs/guide/host-settings.md` (the new pages) and `docs/reference/mcp-tools.md`.

## Risks

- **Degrading on older hosts depends on lazy linking.** The inline accessors work because the JVM
  resolves a class or a method only when an instruction first uses it. Service types are interfaces
  and stay that way. The compatibility test pins the behavior.
- **A bundled copy of a host-provided artifact is ignored.** Parent-first loading means a plugin that
  bundles `jetwhale-ios-xctest-runner` runs the host's version, whatever it was built against.
- **Services outlive class loaders.** A service that kept a plugin's callback or flow would keep a
  disposed plugin's class loader, and everything it loaded, in memory. Services take values, and
  plugins collect their flows in `pluginScope`.
- **Fixes ship with the host.** A runner fix needs a host release. That matches how the official
  plugins ship today; a third-party plugin depends on the host's version of the service.
- **Platform code in a host for three OSes.** The iOS code runs and is tested only on macOS; on Linux
  and Windows its services are null.
- **Two instances of one plugin.** The host-session and app-session instances share storage, and
  their MCP commands must declare the same parameters. Two sidebar entries with one name can confuse,
  so each instance's screen says where it reads from (a device, or the app).
- **Order on an iPhone until step 4.** A plugin other than Mirror that drives an iPhone's runner can
  lose its iproxy connection when Mirror starts the capture.
- **New members on `JetWhaleHostPlugin`.** A plugin that declares a member with the same JVM name
  fails to load.
- **Paths in MCP results.** `jetwhale.getStatus` gives an agent the paths of the user's tools, home
  directory included. They are read-only, under the same permission as the rest of `getStatus`.
- **Unchanged:** the installed app and a development host have different app data directories, so
  they keep separate runner state and can start two runners for one simulator, as they can today.

## Open decisions

- **How a plugin reaches the services**: bound to the instance, or passed to the factory as in #376.
  *Recommendation:* bound to the instance. #376 is reworked to deliver `JetWhaleAdb` as a service, and
  no factory changes.
- **The manifest's shape**: a boolean `runsInHostSession`, a list of session kinds, or a tri-state
  replacing `requiresAgent`. *Recommendation:* the boolean. It adds one case and keeps the two that
  exist; an older host ignores it and runs the plugin as before.
- **How an instance learns its session**: a boolean, or an enum or sealed type. *Recommendation:*
  the boolean `isInHostSession`. It answers the one question plugins have, and a new kind of session
  cannot break a plugin's exhaustive `when`.
- **The sidebar**: two entries for a plugin with both instances, or one that opens the app's instance
  and falls back to the host session's. *Recommendation:* two entries. With one, a user whose app runs
  the agent could not reach the device-wide view.
- **Where the iOS types live**: in the SDK, or in `jetwhale-ios-xctest-runner` provided by the host.
  *Recommendation:* the runner artifact. The SDK stays free of one platform's types, and the client's
  callers keep their imports.
- **Which tools the Tools page lists**: declared in manifests, or the ones plugins looked up during
  this run. *Recommendation:* declared, plus adb and any saved path. The page is then complete before
  a plugin runs, and the manifest records what a plugin needs.
- **A user path that is not executable**: null with the reason, or a fallback to the search.
  *Recommendation:* null with the reason.
- **Tool paths over MCP**: settable, or read-only. *Recommendation:* read-only.
- **The capture helper**: into the host's iOS service, or kept in Mirror. *Recommendation:* into the
  host (step 4), before any plugin other than Mirror drives an iPhone.
- **The runner lease tool**: in Mirror, or in the host. *Recommendation:* the host.
- **Mirror's stored team**: copied into the host setting once, or dropped. *Recommendation:* dropped.
  Driving an iPhone is experimental and has never run on a device; the team is entered again once.
- **Services provided by plugins**: now, or later. *Recommendation:* later, when a third party needs
  to provide one; the lookup leaves room for it.
- **The device list for every platform**: now, or after the iOS service. *Recommendation:* after
  (step 5), so that Android tracking stops depending on port mapping in one change.
