# Plugin versions and compatibility

Status: **proposed**. Phase 2, the protocol Gradle plugin, is built in
[#458](https://github.com/kitakkun/JetWhale/pull/458), and this document describes it as built. The
plugin's source files cited below are #458's, under
`jetwhale-gradle-plugin/src/main/kotlin/com/kitakkun/jetwhale/protocol/gradle/`. Nothing else is
implemented. The wire schema dump and its classification were tried on the official protocol
modules; see [What was checked](#what-was-checked).

A JetWhale plugin is three modules: a protocol module of `@Serializable` messages, an agent module
that apps link, and a host module that the host loads as a jar. This document decides what a
plugin's version is, how the host decides that an agent and a host plugin can talk, and how a plugin
author learns that a change needs a new number. It applies to the official plugins and to
third-party plugins built with JetWhale's Gradle plugins alike.
[#320](https://github.com/kitakkun/JetWhale/pull/320) runs several versions of one host plugin side
by side; this document gives it the version it orders by and the rule it binds with.

## Goal

- **One release version, written once.** The artifact version is the plugin's version everywhere:
  in the manifest, in what the agent sends, in the host's UI and MCP output, and in #320's version
  identity and per-version storage. Nobody types it a second time.
- **Compatibility is a separate integer**, the plugin's *protocol revision*, declared in the
  protocol module that both sides compile against. The host accepts an agent whose revision is in
  the range its host plugin supports.
- **The build says when the revision must change.** A committed dump of the protocol module's wire
  schema, checked like an ABI dump, fails the build when a breaking change comes without a new
  revision.
- **What is already out there keeps working.** Hosts, agents and plugin jars built before this
  negotiate as they do today, with the version range as the fallback.

## Today

### Four hand-written versions

| What | Where | On `main` |
|---|---|---|
| Artifact version | `gradle/libs.versions.toml:2`, set on every project in `build.gradle.kts:40-44` | `1.0.0-alpha14` |
| Manifest `version` | each `META-INF/jetwhale/plugin-manifest.json`, e.g. network's `:7` | `1.0.0`; semantics `1.1.0` |
| Manifest `agentVersionRange` | the same files, `:9-12` | `1.0.0` to `1.0.0`; semantics `1.1.0` to `1.1.0` |
| Agent `pluginVersion` | a literal in each agent plugin, e.g. `JetWhaleNetworkAgentPlugin.kt:44`, `JetWhaleSemanticsAgentPlugin.kt:59` | `"1.0.0"`; semantics `"1.1.0"` |

The manifest is hand-written and shipped as it is (`docs/guide/developing-plugins.md:108-110`;
`packagePlugin` copies the module's jar, `com.kitakkun.jetwhale.host.gradle.kts:39-53`). Nothing ties
the four together. The agent Gradle plugin has already solved the same problem for itself: it
generates its own version as a constant, because "a hand-written literal there is a version-skew
bug waiting for the next release" (`jetwhale-agent-gradle-plugin/build.gradle.kts:32-62`).

### What follows from it

- **Releases share a version.** The 1.0.0-alpha13 network jar and today's alpha14 build both say
  `network 1.0.0`. #320 keys loaded versions by `(pluginId, version)` and stores data per version,
  so to it two releases would be one version with one storage directory. Settings shows `v1.0.0`
  for every network release (`PluginSettingsScreen.kt:496`), and so does MCP
  (`HostPluginCommands.kt:55`, `SessionTools.kt:80`).
- **A pre-release suffix is lost, and the patch number with it.** `PluginNegotiationStrategy.kt:61-76`
  splits a version on `.` and maps a part that is not an integer to 0: `1.0.0-alpha14` becomes
  `[1, 0, 0]`, and `1.2.3-beta1` becomes `[1, 2, 0]`.
- **"Version" means two things, and nothing says when to raise it.** #271 moved semantics to 1.1.0,
  version and range, "so a host that cannot decode an apple node rejects the agent" (`40b4fa83a`):
  a bump of the wire contract. #249 renamed `RedactionTarget.BODY_JSON_FIELD` to `BODY_FIELD`,
  updated the network ABI dumps for the rename (`f4c88b421`), and left network at 1.0.0. An alpha11
  agent and an alpha12 host still pair. When the agent holds an MCP-only body-field rule, the host
  cannot decode its reply to `GetRedactionConfig` (`JetWhaleMessenger.kt:90-92`), and the Network
  Inspector then keeps every transaction from MCP (`NetworkHostPluginFactory.kt:54`, `:94-98`).
- **"Incompatible" is advisory.** Negotiation reports an agent outside the range as incompatible
  (`PluginNegotiationStrategy.kt:30-47`). But the session records every plugin the agent offered
  (`DefaultDebugWebSocketServer.kt:105`), reconciliation creates an instance for each
  (`DefaultPluginSessionReconciliationService.kt:33-37`) and sends `PluginActivated`
  (`DefaultDebugWebSocketServer.kt:121-127`), and the agent activates the plugin without looking at
  the verdict (`JetWhaleAgentPluginService.kt:87-101`). Traced in the code, the same in
  1.0.0-alpha13; not run. Nobody is shown the verdict either: the agent reads only the available
  ids (`DefaultClientSessionNegotiationStrategy.kt:29-31`). #320 binds through the compatibility
  rule, which stops the pairing; showing the verdict is left to this design.

### What the wire already tolerates

- **Negotiation ignores unknown keys** on both sides. `JetWhaleJson` has `ignoreUnknownKeys = true`
  (`JetWhaleJson.kt:16-23`), as it has since 1.0.0-alpha01, and both the host's WebSocket
  (`JetWhaleAppGraph.kt:102`) and the agent (`JetWhaleServiceDsl.kt:44`) use it. A new field on
  `JetWhalePluginInfo` is invisible to every released host and agent.
- **Manifests ignore unknown keys** (`JetWhaleHostPluginManifestFile.kt:51`), since 1.0.0-alpha05,
  the first release with a JSON manifest.
- **Plugin payloads have one format.** `DefaultJetWhaleMessagingFormat` (`JetWhalePluginPeer.kt:20-26`)
  turns on `ignoreUnknownKeys` and `encodeDefaults`, and leaves `explicitNulls` on and
  `coerceInputValues` off, kotlinx.serialization's defaults, with no serializers module. The host's
  peer takes it by default (`DefaultPluginInstanceService.kt:187-192`), as does the agent's peer,
  and the agent's messenger names it (`JetWhaleAgentPluginService.kt:121`, `:52`). It has not
  changed since symmetric messaging shipped in 1.0.0-alpha09.
- **A message is routed by its serial name** (`JetWhaleMessageHandlers.kt:55`, `:68`). A peer skips
  an event it has no handler for, with a log line, and answers a request it has no handler for with
  a failure (`InboundFrameDispatcher.kt:127-134`, `:159-164`).
- **The core protocol already uses an integer**: `JetWhaleProtocolVersion(2)`
  (`JetWhaleProtocolVersion.kt:14-20`), and the host accepts `2..Current`
  (`ProtocolNegotiationStrategy.kt:38`). A plugin's revision is the same idea, per plugin.

## Design

### Two numbers

| | Release version | Protocol revision |
|---|---|---|
| What it is | The artifact version: `1.0.0-alpha15`, `2.3.1` | An integer, from 1, raised by one on each breaking change of the plugin's wire |
| Declared in | The build's `version`, as for any artifact | The protocol module's build script |
| Carried by | The manifest's `version`, the agent's `pluginVersion` | The manifest's `protocolRevisionRange`, the agent's `protocolRevision` |
| Used for | Display, install, #320's identity, order and storage | Whether an agent and a host plugin version can talk |
| Changes | Every release | Only when the wire breaks |

The release version says which build a piece is; the revision says which wire it speaks. Neither
stands in for the other any more. "Revision" rather than "version" keeps it apart from both the
release version and the core protocol's `JetWhaleProtocolVersion`.

### The protocol module declares the revision

A new Gradle plugin, `com.kitakkun.jetwhale.protocol`, goes on a plugin's protocol module. It is
published from the `jetwhale-gradle-plugin` build, next to `com.kitakkun.jetwhale.host`, inside the
same artifact, `jetwhale-host-gradle-plugin` (`jetwhale-gradle-plugin/build.gradle.kts:42-58`).

```kotlin
// the plugin's protocol module — build.gradle.kts
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.kitakkun.jetwhale.protocol") version "<version>"
}

jetwhaleProtocol {
    pluginId.set("com.example.myplugin")
    protocolRevision.set(1)
    contractQualifiedName.set("com.example.myplugin.protocol.MyPluginContract")
}
```

It does three things:

1. **Generates the contract**, a Kotlin file in the common source set:

   ```kotlin
   public val MyPluginContract: JetWhalePluginContract = JetWhalePluginContract(
       pluginId = "com.example.myplugin",
       releaseVersion = "1.4.0",       // project.version
       protocolRevision = 1,           // jetwhaleProtocol.protocolRevision
       wireSchemaHash = "7fa0c0b1…",   // of the committed dump; null before the first one
   )
   ```

   `JetWhalePluginContract` is a new class in `jetwhale-protocol-core`. The agent and the host module
   both compile against the protocol module, so they see the same values.

   The hash is the SHA-256 of the committed dump without its revision line (`WireSchema.kt:51-53`),
   so raising the revision alone keeps it. It is generated from the committed file, and
   `dumpWireSchema` writes that file only after the classes it reads, the contract among them, are
   compiled. Right after `dumpWireSchema`, the hash is therefore one build behind: the build that ran
   it compiled the previous hash, and the next build compiles the new one
   (`GenerateJetWhalePluginContractTask.kt:64`).
2. **Dumps and checks the wire schema** (next section).
3. **Puts the contract into the JVM jar** as `META-INF/jetwhale/protocols/<pluginId>.json`, where
   the host Gradle plugin reads it from the classpath without loading a class.

The release version is the protocol module's `version`. A plugin's three modules publish together
under one version, as the official ones do (`build.gradle.kts:40-44`), and the host Gradle plugin
holds the host module to it
(see [The build writes the release version](#the-build-writes-the-release-version)). An agent
module released on its own still reports its protocol module's version.

### The wire schema dump

`dumpWireSchema` writes `wire/<project>.wire` in the protocol module, committed like the ABI dumps
in `api/`. `checkWireSchema` runs in `check` and fails when the committed dump is not what the
compiled classes produce, or records another revision than the build script. Both compare with what
`extractWireSchema` reads from the classes into the build directory
(`JetWhaleProtocolPlugin.kt:46-74`).

Given `-PjetwhaleWireSchemaBaselineDir=<dir>`, a directory that holds an earlier dump of each module
laid out as in the repository, `checkWireSchema` also fails when the committed dump could not have
followed the earlier one, under the rule `dumpWireSchema` applies (below). That catches a dump edited
by hand (`CheckWireSchemaTask.kt:62-65`). A relative `<dir>` is resolved against the root
project, and a module with no earlier dump is checked against nothing.

**How the schema is read.** Reading serializers initializes the module's classes, so
`extractWireSchema` reads them in a process-isolated Gradle worker. That worker's class loader
carries Gradle's own Kotlin (2.3.20 under this repository's Gradle 9.5.1) and the build script's
classpath, with a kotlinx.serialization that need not be the module's. The reader therefore runs in
a `URLClassLoader` of its own inside the worker, holding the module's classes, the module's runtime
classpath and the reader, under the platform class loader, so the kotlinx.serialization that builds
the descriptors is the one the module ships (`ExtractWireSchemaTask.kt:63-91`).

The reader takes every concrete class that implements `JetWhaleEvent` or `JetWhaleRequest` and gets
its serializer's descriptor. It resolves a request's reply type `R` by reflection, through generic
supertypes: in `Ping : BaseRequest<Pong>`, where `BaseRequest<R> : JetWhaleRequest<R>`, the reply is
`Pong`. A reply type that no supertype binds is refused (`WireSchemaReader.kt:181-213`). From these
roots it walks the descriptors: element names and types, nullability, which elements are optional,
enum entries, sealed subtypes and the class discriminator. A value class is recorded as the value it
encodes to.

Two property annotations change the wire beyond the descriptor's shape. `@JsonNames` is in the
descriptor's element annotations. `@EncodeDefault` is not, since the serialization plugin compiles
it into the serializer, but Kotlin keeps it, with runtime retention, on the property's synthetic
`<getter>$annotations` method, where reflection reads it; for a property whose type is a value
class, that method's name carries the getter's mangling. The reader reads these methods on the
module's classes up front. A class from a dependency is reached rarely, so the reader scans the
runtime classpath for `@Serializable` classes lazily, the first time the walk reaches a class from
outside the module, and reads them the same way (`WireSchemaReader.kt:57-132`). The dump records
both annotations.

Why compiled classes rather than a compiler-plugin side output: the descriptors are what
kotlinx.serialization encodes with, custom serializers' included, so the dump cannot disagree with
the wire. A compiler plugin would have to derive serial names, optionality and sealed subtypes from
IR again, which is the serialization plugin's own work. Every protocol module has a JVM target,
because the host plugin compiles against it. The plugin reads the classes of exactly one: a
multiplatform module's JVM target (an Android target is not one), or a Kotlin JVM module's. It
refuses a module with none, and one with more than one (`JetWhaleProtocolPlugin.kt:89-103`).
Descriptors come from common code, so the JVM ones stand for every target. The KMP network and
semantics modules' JVM jars gave the same dump as JVM-only compiles of their sources.

**What is refused.** The task fails with the path of what it refuses, as #436's generator does
(`WireSchemaReader.kt:137-311`). Two kinds of type cannot be sent at all:

- **Open polymorphism and contextual serializers.** They need a serializers module, and the payload
  format has none (`JetWhalePluginPeer.kt:20-26`).
- **A map whose key is neither a primitive nor an enum**, nor a value class over one. The payload
  format leaves `allowStructuredMapKeys` off, so nothing else can key a JSON object.

The rest would make a dump that does not say what the wire carries:

- **A generic class reached with two different type arguments**, or two classes with one serial
  name: one serial name for two shapes.
- **A message whose serializer describes something other than a class, an object or an enum**, such
  as a value class. A message is routed by its serial name, and the dump records it as the shape of
  that name.
- **A field, a reply or a type argument whose type carries a built-in's serial name**, such as `int`
  or `list`. A type reference names a shape by its serial name, so it would read back as the
  built-in. A message or a sealed subtype may carry one, since the dump lists those by name and never
  as a type: semantics' `ViewAttributeValue` has subtypes that serialize as `float` and `int`.
- **A name that would not read back from the dump**: a type's serial name with `<`, `>`, `,` or
  `?`, a field's with `: `, an `@JsonNames` name with `, ` or ` | `, and any name that starts or ends
  with a space or holds a line break. The reader parses the dump it renders and refuses when the two
  differ.

**The format** is sorted text, one declaration per block, meant to be read in a diff. The network
protocol's begins:

```
revision 1

request network/get_mock_config -> network/mock_config
request network/get_redaction_config -> network/redaction_config
event network/request_failed
event network/request_sent
event network/response_received
request network/set_mock_rules -> network/ack
request network/set_mocking_enabled -> network/ack

enum com.kitakkun.jetwhale.plugins.network.protocol.BodyEncoding
  BASE64
  TEXT

class com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
  body: string? = …
  bodyEncoding: com.kitakkun.jetwhale.plugins.network.protocol.BodyEncoding = …
  bodyTruncated: boolean = …
  headers: map<string, list<string>> = …
  method: string
  timestampMs: long
  txId: string
  url: string
```

A type is a built-in or a shape's serial name. The built-ins are the primitives (`boolean`, `byte`,
`char`, `double`, `float`, `int`, `long`, `short`, `string`), `list<…>` and `map<…, …>`, and
kotlinx.serialization's JSON elements, written `json`, `json-object`, `json-array`, `json-primitive`
and `json-null` (`WireSchema.kt:144-148`). A shape is a `class` with its fields, an `object`, an
`enum` with its entries, or `sealed <name> by <discriminator>` with its subtypes' serial names.

`= …` marks an optional element, one with a default. `, omitted at default` follows it for an
element under `@EncodeDefault(NEVER)`, and `| also read as` lists an element's `@JsonNames`. The
defaults themselves are not in a descriptor and need not be: the payload format encodes defaults,
so a sender sends every field that is not omitted at its default, and a receiver's default only
fills in a field its peer did not know.

**When the revision must change.** `dumpWireSchema` compares the new schema with the committed one.
Messages are matched by serial name, which is what the wire carries. From each message the two
schemas are walked side by side, so renaming a Kotlin class that is not polymorphic changes the
dump's text but not the verdict. A change's path starts at the message that reaches it, such as
`network/get_redaction_config (reply).mcpOnlyRules[].target`. A type that several messages reach is
compared once, so a change to it is listed once, at the first path that reaches it, with messages
and fields taken in sorted order (`WireSchemaClassifier.kt:62-63`). If a change is breaking and
`protocolRevision` still equals the dump's `revision`, the task fails, lists each breaking change with
its path, and names the revision to set. A raised revision lets any change through; a lowered one
fails. The first dump must be at revision 1, which the rule below relies on: `dumpWireSchema` refuses
to write one at another revision (`ProtocolRevisionRule.kt:15-23`).

### What counts as breaking

A change is breaking when a peer built before it, or after it, could fail to decode or route what
the other one sends. The verdicts follow from the payload format:

| Change | Verdict | Why |
|---|---|---|
| Add an optional field | additive | The older peer ignores the key; the newer one fills in the default |
| Add a required field | breaking | The older peer never sends it |
| Remove an optional field | additive | The older peer falls back to its default |
| Remove a required field | breaking | The older peer still requires it |
| Make a required field optional | additive | `encodeDefaults` keeps it on the wire |
| Make a required field optional under `@EncodeDefault(NEVER)` | breaking | The newer peer leaves it out at its default |
| Add or remove `@EncodeDefault(NEVER)` on an optional field | additive | The field is optional on both sides, so a peer that does not receive it falls back to its default |
| Make an optional field required | breaking | |
| Change a field's type, or its nullability either way | breaking | |
| Rename a required field's serial name, even keeping the old one in `@JsonNames` | breaking | One required field removed and one added; `@JsonNames` only lets the newer peer read the old name |
| Rename an optional field's serial name | additive | One optional field removed and one added; the value is lost between versions, which is a matter of meaning (below) |
| Add or remove a field's `@JsonNames` name, keeping its serial name | additive | Peers send the serial name; the other names only change what one side also reads |
| Add or remove an enum entry | breaking | An unknown entry does not decode: `coerceInputValues` is off |
| Add or remove a sealed subtype, or change the discriminator | breaking | |
| Add an event | additive | The older peer skips it |
| Add a request | breaking | The older peer answers with a failure, which `request` throws |
| Remove an event, or change its serial name | additive | Matched by serial name, so the newer peer skips the older one's event and the older peer stops receiving it; whether the feature still works is a matter of meaning |
| Remove a request, or change its serial name | breaking | The older peer still sends it and gets a failure |
| Turn an event into a request or back, or change a reply type | breaking | |
| Rename a Kotlin class that is not polymorphic, reorder fields, edit an `@McpDescription` | no wire change | |

A new request is breaking because nothing makes its sender ready for a peer without it. A request
made while preparing fails the preparation: the network host asks for `GetMockConfig` there and
lets a failure propagate (`NetworkHostPluginFactory.kt:82`). With a new revision, a host that keeps
`min` at the older one knows which agents lack the request and leaves it out for them
([open decision 1](#open-decisions)).

What the check cannot know, and says in its output:

- **Direction.** The schema does not say which side sends a message, so an enum entry that only the
  agent sends counts as breaking both ways. The host's revision range is how a plugin keeps serving
  older agents across such a change (next section).
- **Meaning.** A field whose meaning changes, a default that now means something else, or a custom
  serializer that writes its string differently: all of them still decode. Their author raises the
  revision by hand.

### The host plugin declares a range

The manifest gets `protocolRevisionRange`, both ends inclusive:

```json
{
  "pluginId": "com.example.myplugin",
  "pluginName": "My Plugin",
  "version": "1.4.0",
  "factoryClass": "com.example.MyPluginFactory",
  "protocolRevisionRange": { "min": 2, "max": 3 },
  "agentVersionRange": { "min": "1.0.0", "max": "1.2.0" }
}
```

- `max` is the revision the host plugin was compiled against. The host Gradle plugin writes it.
- `min` is the oldest revision the plugin still serves, written by its author in the source
  manifest. Without it, `min` is `max`. A break that only the agent sends needs no host code to keep
  serving older agents: semantics' `AppleNode` was a new subtype that only agents send, so a host
  that knows it decodes an older agent's tree. A break the host sends, a new request among them,
  needs the instance to know its agent's revision: `agentProtocolRevision` on
  `JetWhaleMessagingHostPlugin`, `null` for an agent that sends none.
- `agentVersionRange` now matters only when one side has no revision: which older agents this jar
  serves, and which newer agents an older jar serves (next section).
- A range is well-formed when `1 <= min <= max`. The host Gradle plugin fails the build on any other
  range, as `com.kitakkun.jetwhale.protocol` does on a `protocolRevision` below 1. The host refuses
  to load a jar whose range is not well-formed, as it refuses a manifest it cannot parse, so the
  jar is listed among the failed ones in Settings.

The agent sends one revision and does not negotiate down. An app pins its agent, and the host is the
side a developer can update in one click, so the range belongs to the host.

### The rule

One rule decides, in negotiation and in #320's binding:

```
accepts(manifest, agentPlugin):
    revision = agentPlugin.protocolRevision
    revisions = manifest.protocolRevisionRange
    versions = manifest.agentVersionRange
    if revision != null and revisions != null:     // both have revisions
        return revision in revisions
    if revision != null:                            // a jar from before revisions
        return revision == 1 and (versions?.contains(agentPlugin.pluginVersion) ?: true)
    if revisions != null:                           // an agent from before revisions
        return 1 in revisions and versions?.contains(agentPlugin.pluginVersion) == true
    return versions?.contains(agentPlugin.pluginVersion) ?: true    // neither, as today
```

A side from before revisions speaks revision 1 at best. Revision 1 is the wire a plugin had when it
adopted revisions, and every later revision is a break since then, which nothing built earlier can
speak. So a jar from before revisions serves only revision-1 agents, and an agent from before
revisions is served only while the host plugin still accepts revision 1. Such an agent is also
served only when the manifest keeps an `agentVersionRange` that includes it: a manifest that has a
revision range opts in to older agents explicitly, and an empty `agentVersionRange` object accepts
every version. Only when neither side has revisions does a missing range still accept any agent,
as it does today.

Versions compare with #320's `PluginVersionOrder`, in two strengths:

- **Which loaded version is newest** uses the full order: release parts as numbers, a release after
  its pre-releases, pre-release numbers as numbers, so `1.0.0-alpha9` < `1.0.0-alpha10` < `1.0.0`.
  Before it, the manifest's `manifestFormat`: a jar whose manifest the new host Gradle plugin wrote
  (format 2) is newer than every jar of the same plugin with a hand-written version (format 1, or
  no `manifestFormat`). The hand-written versions are not release versions. Every official jar so
  far says `1.0.0` (semantics `1.1.0`), which the full order puts above `1.0.0-alpha15`, the first
  release version, so without the format the older jar would win. Within a format, the order
  decides.
- **A range bound** compares release parts only, as #320's `compareReleases` does: `1.2.3-beta1`
  keeps its patch number, and `1.3.0-beta1` is inside a range that starts at `1.3.0`. The bounds a
  jar without a revision range carries were written for hand-typed agent versions: the official
  ones name a bare `1.0.0`. An agent built after this reports `1.0.0-alpha15`, which the full order
  puts below `1.0.0`, so every such agent would be refused by every official jar of an earlier
  release ([open decision 6](#open-decisions)).

`HostVersion` in `:jetwhale-host:release-metadata` is not reused: it accepts only JetWhale's own
tag format, and a third-party version can be anything.

### The build writes the release version

- **Host.** The host Gradle plugin generates the manifest that goes into the jar. It writes
  `version` from `project.version`, `manifestFormat: 2` to mark that version as a release version,
  and `protocolRevisionRange.max` from the contract with the same `pluginId` on the module's runtime
  classpath. Everything else stays as written. The build fails
  when the source manifest has a `version` or a `max` that disagrees, when `project.version` is
  unspecified, when the contract's release version is not `project.version`, and when a contract
  on the classpath matches no entry's `pluginId`, which is a typo on one side. The release-version
  check keeps the agent and the host reporting one version for one release; the manifest keeps the
  host module's own, because it is the jar's identity in #320 and what an install compares with
  the coordinates. An entry with `requiresAgent: true` and no contract gets no range and a warning:
  it is matched by `agentVersionRange`, as today.
- **Agent.** `JetWhaleAgentPlugin` takes the contract in place of `pluginId` and `pluginVersion`:

  ```kotlin
  class MyAgentPlugin : JetWhaleAgentPlugin() {
      override val contract = MyPluginContract
  }
  ```

  The runtime sends `pluginId`, `pluginVersion` (the release version), `protocolRevision` and
  `wireSchemaHash` from it. Nothing is left to type, so nothing can disagree. The agent module needs
  no Gradle plugin: the values come from the protocol module it already depends on.
  `JetWhalePluginContract` has a public constructor for agents that have no protocol module, such
  as the QA agent.

### What the user sees

- **Versions.** Settings, #320's sidebar badge and MCP output show release versions.
- **Verdicts.** A session's plugin page names an incompatible agent's release version and revision,
  the installed versions' ranges, and which side to update. MCP's session listing carries the same.
  The agent logs each plugin the host reported incompatible, with its version and revision.
- **Installs.** A catalog or Maven install knows its coordinates. When a loaded manifest's `version`
  differs from the coordinates' version, the install succeeds with a warning naming both: the jar
  was built by hand or with an older Gradle plugin. A `-SNAPSHOT` is compared as written, which is
  how both carry it.

### Third-party plugins

**Opting in** takes three steps, all delivered by upgrading JetWhale's Gradle plugins and SDK:

1. Apply `com.kitakkun.jetwhale.protocol` to the protocol module, set `pluginId`,
   `protocolRevision` to 1 and `contractQualifiedName`, run `dumpWireSchema`, and commit `wire/`.
2. In the agent plugin, replace `pluginId` and `pluginVersion` with `override val contract`.
3. Delete `version` from the source manifest. Keep or add `agentVersionRange` for as long as agents
   without a revision should be served; `{}` serves all of them. Without it they are refused.

Upgrading only `com.kitakkun.jetwhale.host` already writes `version`. Without step 1 the plugin is
matched by its version range, as before.

**Plugins built before this keep working.** A jar and an agent that both predate revisions are
matched as today; only the comparison changes, to release parts as numbers, so a patch number after
a pre-release suffix now counts. When only one side predates revisions, the rule above applies:
revision 1, and the version range.

**What the host cannot do.** It cannot make a plugin raise its revision when the plugin was built
without JetWhale's Gradle plugins or ships a dump edited by hand. It detects and reports:

- an agent and a host plugin that claim the same revision with different `wireSchemaHash`es:
  additive differences, or a break that nobody recorded. Shown in the plugin's diagnostics, not as
  an error;
- plugin frames that fail to decode, which `InboundFrameDispatcher` only logs today, counted per
  plugin and shown with both sides' versions, revisions and hashes;
- a manifest version that is not the installed coordinates' version (above).

### Swift agents

[#436](https://github.com/kitakkun/JetWhale/pull/436) proposes, for a Swift agent side of a Kotlin
host plugin, a generator that loads the protocol module's JVM classes, reads the same descriptors and
reply types, writes Swift `Codable` types, and has the agent send a per-plugin contract hash, under
a capability key or in a field on `JetWhalePluginInfo`.

That generator and this dump are one component: the schema reader in
`com.kitakkun.jetwhale.protocol`. Swift sources are a second output of the same model, #436's
contract hash is `wireSchemaHash`, and its open question is settled in favor of the field on
`JetWhalePluginInfo`, which this design adds anyway. A Swift agent sends the revision and the hash
from generated Swift constants and is negotiated like a Kotlin one.

The revision and the hash stay two values because they answer two questions. The revision says
whether two sides can talk, and changes only on a break. The hash says whether two sides were built
from the same schema, and changes on any change the dump can see: an additive one, and even a
Kotlin rename that the wire never sees. It does not change with what the dump leaves out, such as a
default value or a custom serializer's format, so equal hashes do not prove equal behavior.
Negotiation needs the first, #436's strict decoding the second, and one dump gives both.

### What #320 changes

1. **Order.** Identity stays `(pluginId, version)` and the order stays `PluginVersionOrder`; the
   version becomes the release version. A higher `manifestFormat` comes first, so a jar built by
   the new Gradle plugin outranks every hand-versioned jar of its plugin, in binding and in what an
   install replaces. `PluginVersionOrder` puts `X-SNAPSHOT` before `X`: it now ranks
   `1.0.0-alpha14-SNAPSHOT` after `1.0.0-alpha14`, because the suffix reads as one more pre-release
   segment.
2. **Binding.** `AgentVersionCompatibility(agentVersion)` becomes the rule above and takes the
   agent's `JetWhalePluginInfo`. "The newest version whose `agentVersionRange` accepts the agent"
   becomes "the newest version that accepts the agent", by revision when both sides have one.
   Bounds keep comparing with `compareReleases`.
3. **Storage.** Data is per release version, so each update adds a directory, seeded by copy. A
   version's directory goes when its jar is removed, unless it is the newest.
4. **Replacing.** With release versions, every update is a new version, so what an install does
   with the jars it supersedes, which #320 leaves open, needs its answer: a catalog or Maven install
   replaces the older versions unless an active session is bound to one, which is the case side by
   side exists for.

**Order of landing.** #320 lands first, after #316, as it is or with item 1. Phase 1 below changes
its compatibility class (item 2). Items 3 and 4 are #320 follow-ups that nothing here waits for.

## Wire and API changes

**Wire, additive only.**

- `JetWhalePluginInfo` gains `protocolRevision: Int? = null` and `wireSchemaHash: String? = null`.
  Released hosts and agents ignore them, and a newer side reads `null` from an older one. The host
  fills them in `availablePlugins` with the bound version's values.
- The manifest gains `protocolRevisionRange` with integer `min` and `max`, and an integer
  `manifestFormat`, 2 when the Gradle plugin wrote `version`. Released hosts ignore both. A jar's
  manifest still has `version`, which released hosts require; the source may leave it out.
  `schemas/plugin-manifest.schema.json`, which allows no other properties (`:8`, `:17`), adds both
  and makes `version` optional, since it describes the source.
- The protocol jar carries `META-INF/jetwhale/protocols/<pluginId>.json` with `pluginId`,
  `releaseVersion`, `protocolRevision` and `wireSchemaHash`. The build reads it; the host does not.

**API.**

- `jetwhale-protocol-core`: `JetWhalePluginContract`.
- `jetwhale-agent-sdk`, **Breaking**: `JetWhaleAgentPlugin.contract` replaces `pluginId` and
  `pluginVersion`.
- `jetwhale-host-sdk`: `JetWhaleHostPluginManifest.protocolRevisionRange`, and
  `agentProtocolRevision` on `JetWhaleMessagingHostPlugin`.
- Gradle: `com.kitakkun.jetwhale.protocol`, whose `jetwhaleProtocol` extension has `pluginId`,
  `protocolRevision` and `contractQualifiedName`, with `dumpWireSchema` and `checkWireSchema`, and
  the `jetwhaleWireSchemaBaselineDir` Gradle property for the baseline comparison.
  `com.kitakkun.jetwhale.host` writes the manifest's `version` and `max`.
- QA agent: `--plugin <id>[@<version>][#<revision>]`, and `runJetWhaleQaAgent` passes the module's
  revision.

## Migration

### Official plugins

- Every official protocol module starts at revision 1, the wire of the release that adopts
  revisions.
- Manifests drop `version` and keep `agentVersionRange` for the agents already in apps: `1.0.0` to
  `1.0.0` for network, nav3, storage, actions and example, `1.1.0` to `1.1.0` for semantics. Those
  agents are served as today. They cannot be told apart any further: an alpha09 and an alpha13
  network agent both say `1.0.0`, though #249's rename lies between them.
- The first release under this installs next to the hand-versioned jars already in
  `~/.jetwhale/plugins`. Its `manifestFormat` makes it the newest, so sessions bind it and an
  install replaces the old jar rather than the reverse.
- When an official plugin later raises `min` above 1, agents without a revision stop being served
  by the rule itself, and its `agentVersionRange` can go.
- A raised revision is a **Breaking** changelog entry that says to update the host and the agent
  together, as `agents/release.md` already requires for such a wire change.

### Every combination

H0 is a host released before this, H1 one after. J0 is a host plugin jar whose manifest has no
revision range, J1 one built with the new Gradle plugins. A0 is an agent that sends no revision and
a hand-written version, A1 one that sends a contract. Every host compares range bounds by release
numbers: dotted numbers on `main`, release parts with #320 and after. Before #320 a host reports the
verdict without enforcing it (see [Today](#what-follows-from-it)); with #320 it enforces it.

| Host | Jar | Agent | Decided by | Outcome |
|---|---|---|---|---|
| H0 | J0 | A0 | `agentVersionRange` | As today |
| H0 | J0 | A1 | `agentVersionRange` against the release version | `1.0.0-alpha15` counts as `1.0.0`: network, nav3, storage, actions and example agents are accepted until the release version passes 1.0.0, and semantics agents are refused (`1.0.0` < `1.1.0`). Accepted is not compatible; see below. Update the host |
| H0 | J1 | A0 | the kept `agentVersionRange` | As today |
| H0 | J1 | A1 | as H0, J0, A1 | As H0, J0, A1 |
| H1 | J0 | A0 | `agentVersionRange` | As today, except that `1.2.3-beta1` keeps its patch number |
| H1 | J0 | A1 | revision 1 only, then `agentVersionRange` against the release version | As H0, J0, A1 for revision-1 agents, with the same caveat; an agent at revision 2 or later is refused. Update the plugin |
| H1 | J1 | A0 | the jar still accepts revision 1, and its kept `agentVersionRange` includes the agent | As today for official plugins while `min` is 1; a third-party jar with no `agentVersionRange` refuses these agents |
| H1 | J1 | A1 | the revision against the range | The design |

H0 rows cannot change. An H0 host cannot see revisions, so it also pairs an agent at a revision its
jar cannot speak, as it pairs mismatched agents today. Two rows are worse than today, both only for
semantics agents, and both because an official jar from before this compares a release version with
a range written for `1.1.0`: H0, J0, A1, fixed by updating the host, and H1, J0, A1, fixed by
updating the plugin. The second is the usual state right after a host update, since plugins are not
updated with the host ([Host updates, Non-goals](./host-updates.md#non-goals)). Phases 1 and 4 ship
in one release, whose notes say to update the host and then the official plugins from the catalog.
An H0 host installs official jars of its own version (`OfficialPluginRelease.kt:21-33`), so it
rarely meets J1. A third-party jar that adopts revisions without an `agentVersionRange` refuses
older agents on purpose.

Accepted does not mean compatible wherever one side predates revisions. Revision 1 is the wire at
adoption, not the wire of every earlier release, and a version range cannot tell the earlier
releases apart. A known case: a network jar from 1.0.0-alpha11 or earlier accepts a revision-1
agent, both under its `1.0.0` range in a released host and under the revision-1 fallback in a new
one, but #249's `RedactionTarget` rename lies between them, so an MCP-only body-field rule fails to
decode and MCP sees no transactions, as described in [Today](#what-follows-from-it). The decode
failures are counted on the plugin's page (see
[What the host cannot do](#third-party-plugins)), and updating the plugin from the catalog ends
them. The fallback accepts these pairs rather than refusing every jar from before revisions, which
would cut off every plugin that is not updated together with the host.

## What was checked

A scratch tool, not part of this change, implemented the reading, the dump and the classification:
a JVM program that loads a protocol jar in a class loader, walks the descriptors from every
`JetWhaleEvent` and `JetWhaleRequest`, writes the format above, parses it back and compares two
dumps. Kotlin 2.4.10 and kotlinx.serialization 1.11.0, as the repository uses.

- **Fidelity.** The JVM jars of the KMP network and semantics protocol modules, built in this
  repository, gave byte for byte the same dumps as JVM-only compiles of the same sources. Network:
  7 messages and 22 types. Semantics: 5 messages and 30 types.
- **History.** Network at 1.0.0-alpha09 to alpha13 and `main`, and semantics at alpha11 to `main`,
  each dumped at revision 1 and compared with the release before:
  - network alpha11 to alpha12: **breaking**. `RedactionTarget` lost `BODY_JSON_FIELD` and gained
    `BODY_FIELD` (#249), reached through the reply to `network/get_redaction_config`. Additive:
    `bodyEncoding` on captured requests, captured responses and mock responses. Network stayed at
    1.0.0, so nothing told the two releases apart.
  - semantics alpha11 to alpha12: **breaking**. A root's `node` became the sealed `UiNode` (#243),
    `NodeAction` gained `BringIntoView` and `ScrollToIndex`, and three requests were added.
    Additive: two optional fields. #271's manual move to 1.1.0 landed in the same release and
    covered these too.
  - Every other step: no change.
- **One change at a time.** Twenty-one single edits of today's network or semantics protocol, each
  classified as the table says:
  - additive: an optional field added, an optional field removed, a required field made optional,
    an event added, an event renamed;
  - breaking: a required field added, a required field removed, a type changed, a field made
    nullable, an optional field made required, a required field made optional under
    `@EncodeDefault(NEVER)`, an enum entry added, a required field renamed, the same with
    `@JsonNames` for the old name, a request added, a reply type changed, an event turned into a
    request, a sealed subtype added;
  - no wire change: fields reordered and an `@McpDescription` edited, both with the same dump and
    hash; `CapturedHttpRequest` renamed in Kotlin, with a new dump text and hash.

  With the revision raised to 2, the sealed-subtype change passed. `@EncodeDefault` was read from the
  synthetic annotations method, as described above; it is not in the descriptor.

#458 then built the reading, the dump and the classification into `com.kitakkun.jetwhale.protocol`.
Its committed network and semantics dumps are byte for byte the scratch tool's, and its TestKit
tests run the Gradle wiring, the isolated worker and the configuration cache included, on KMP and
JVM-only fixtures.

Not tried: manifest generation, and anything at runtime.

## Testing

- **The rule.** A table test with one case per H1 row of the matrix: `null` revisions on either
  side, a jar from before revisions meeting revisions 1 and 2, a revision range with `min` 1 and 2
  meeting an agent without one, a missing and an empty `agentVersionRange`, bounds at pre-releases
  and snapshots, and a jar refused for a range with `min` 0 or `min` above `max`.
- **Order.** `PluginVersionOrder` with a snapshot before its release, and a format-2 `1.0.0-alpha15`
  above a format-1 `1.0.0`, added to #320's cases.
- **Wire.** Serialization tests that decode the JSON a 1.0.0-alpha13 agent sends into the new
  `JetWhalePluginInfo`, and the new JSON into a class shaped like the old one; the same for the
  manifest.
- **Reader and classifier.** One unit test per row of the classification table, from fixtures like
  the ones above, and the network alpha11 and alpha12 dumps as a regression case.
- **Gradle.** TestKit tests of `com.kitakkun.jetwhale.protocol` on small KMP and JVM-only fixtures
  (the same dump from both, generate, dump, check, refuse a break without a new revision, each
  refusal above, the baseline comparison) and of the host plugin's manifest (version written, a
  mismatch fails, `max` from a contract jar, a warning without one), with the configuration cache on.
- **This repository.** `checkWireSchema` runs in `check` for every official protocol module. The
  base-branch comparison is not part of `check`: a workflow of its own, `wire-schema-check.yml`,
  copies the base branch's `wire/*.wire` into a directory laid out as in the repository and runs
  `checkWireSchema` with `-PjetwhaleWireSchemaBaselineDir` pointing at it. That keeps `check`'s
  invocation, and its configuration cache entry, as they are.
- **By hand.** A sandboxed host with two versions of the example plugin, at revisions 1 and 2 with
  `min` 1, and QA agents at revisions 1, 2 and 3 and without one.

## Risks

- **A dump edited by hand passes `checkWireSchema`** without a baseline. The check proves the dump is
  current, not that the previous one was respected. The base-branch comparison closes the gap where
  CI passes `-PjetwhaleWireSchemaBaselineDir`, as this repository's does; elsewhere it is a diff in
  review.
- **Verdicts are conservative.** A break in a direction nobody uses still asks for a new revision.
  The cost is one number, and a host `min` that keeps serving older agents when the author knows
  better.
- **The build runs the module's classes.** Reading serializers initializes protocol classes inside a
  Gradle worker. Data classes do nothing there; process isolation contains anything else.
- **Storage grows** under #320, one directory per release, until its item 3 lands.
- **Official jars from before this refuse new semantics agents**, in a released host or a new one
  (the matrix). The release notes say to update the host and then the official plugins.
- **More revisions.** A new request raises the revision. A host plugin that keeps `min` at the older
  revision has to leave the request out for older agents, through `agentProtocolRevision`; one that
  raises `min` stops serving them.

## Open decisions

1. **Is a new request breaking or additive?** Recommendation: breaking, as the table has it. Additive
   would leave every sender to guard every new request against an older peer, and nothing enforces
   that: a request made while preparing already lets a failure through. Breaking costs a revision,
   and a host that keeps `min` keeps serving older agents. The alternative is additive, with the
   agent sending the request types it handles so that the host can skip the others; that is a
   second compatibility channel beside the revision.
2. **Does the contract replace `pluginId` and `pluginVersion`, or join them?** Recommendation:
   replace. Side by side, both could be set and disagree, and agents would keep typing versions. The
   price is that third-party agent libraries are rebuilt against the new SDK, stated as Breaking.
3. **Send `wireSchemaHash` now, or with #436?** Recommendation: now. It is one nullable field that
   #436 needs anyway, a wire field is settled once, and until #436 the host uses it for the
   diagnostic above.
4. **Where does `min` live?** Recommendation: in the source manifest, per entry, since a jar can hold
   several plugins and the rest of each entry is there. The alternative is a map keyed by `pluginId`
   in the `jetwhalePlugin` extension, next to the module's other build settings.
5. **Where does the release version come from?** Recommendation: the protocol module. The other
   candidate, the agent module through `com.kitakkun.jetwhale.agent`, would give that plugin a
   second job: it is applied to apps to bake in the build machine's address
   (`JetWhaleAgentSubplugin.kt:13-19`), and the revision has to come from the protocol module anyway.
6. **How do range bounds compare?** Recommendation: by release parts, as #320 does, while "newest"
   uses the full pre-release-aware order. Comparing bounds pre-release-aware as well would refuse
   every new agent at every official jar from before this, whose ranges name a bare `1.0.0` or
   `1.1.0`, in new hosts too. Its gain is small: bounds are hand-written only for agents and jars
   from before revisions, whose versions carry no pre-release suffix.

## Phased PRs

1. **Host and wire.** The `JetWhalePluginInfo` fields, the manifest's range and format and their
   schema, the order by format, the rule in negotiation and in #320's binding, the fallback's
   comparison, `agentProtocolRevision`, the verdict on the plugin page and in MCP, the agent's log
   line, and the protocol reference. Tested with manifests and infos built by hand; nothing
   produces revisions yet.
2. **`com.kitakkun.jetwhale.protocol`**, built in #458. `JetWhalePluginContract`, the generated
   contract, the jar resource, `dumpWireSchema` and `checkWireSchema`, applied to network; then the
   other official protocol modules at revision 1, and the base-branch comparison in a CI workflow of
   its own.
3. **Manifest generation** in `com.kitakkun.jetwhale.host`. Official manifests drop `version`.
4. **Agent SDK, Breaking.** `contract`, sent by the runtime; the official agents and the QA agent;
   `runJetWhaleQaAgent` passing the revision; the plugin guide's opt-in steps.
5. **The install warning** for a manifest version that differs from the coordinates.

#320 lands before 1. Phases 1 and 2 are independent of each other; 3 and 4 need 2; 1 and 4 ship in
the same release. #436's Swift generator builds on 2. Each phase updates the guides it changes.
