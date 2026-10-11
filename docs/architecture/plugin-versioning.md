# Plugin versions and compatibility

Status: **proposed**. Nothing here is implemented. The wire schema dump and its classification were
tried on the official protocol modules; see [What was checked](#what-was-checked).

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
  peer takes it by default (`DefaultPluginInstanceService.kt:187-192`), the agent's messenger names
  it (`JetWhaleAgentPluginService.kt:52`), and it has not changed since symmetric messaging shipped
  in 1.0.0-alpha09.
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
published from the `jetwhale-gradle-plugin` build, next to `com.kitakkun.jetwhale.host`.

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
2. **Dumps and checks the wire schema** (next section).
3. **Puts the contract into the JVM jar** as `META-INF/jetwhale/protocols/<pluginId>.json`, where
   the host Gradle plugin reads it from the classpath without loading a class.

The release version is the protocol module's `version`. A plugin's three modules publish together
under one version, as the official ones do (`build.gradle.kts:40-44`); a plugin that versions them
apart reports its protocol module's.

### The wire schema dump

`dumpWireSchema` writes `wire/<project>.wire` in the protocol module, committed like the ABI dumps
in `api/`. `checkWireSchema` runs in `check` and fails when the committed dump is not what the
compiled classes produce, or records another revision than the build script.

**How the schema is read.** The task loads the module's compiled JVM classes in a Gradle worker,
with the module's own runtime classpath, so the kotlinx.serialization that reads the descriptors is
the one the module ships. It takes every concrete class that implements `JetWhaleEvent` or
`JetWhaleRequest`, gets its serializer's descriptor, and reads a request's reply type `R` from its
`JetWhaleRequest<R>` supertype by reflection. From these roots it walks the descriptors: element
names and types, nullability, which elements are optional, enum entries, sealed subtypes and the
class discriminator. A value class is recorded as the value it encodes to.

Why compiled classes rather than a compiler-plugin side output: the descriptors are what
kotlinx.serialization encodes with, custom serializers' included, so the dump cannot disagree with
the wire. A compiler plugin would have to derive serial names, optionality and sealed subtypes from
IR again, which is the serialization plugin's own work. Every protocol module has a JVM target,
because the host plugin compiles against it; the plugin refuses a module without one. Descriptors
come from common code, so the JVM ones stand for every target. The KMP network and semantics
modules' JVM jars gave the same dump as JVM-only compiles of their sources.

**What is refused.** Open polymorphism and contextual serializers need a serializers module, and the
payload format has none (`JetWhalePluginPeer.kt:20-26`), so such a type cannot be sent at all; the
task fails with the property's path, as #436's generator does. A generic class reached with two
different type arguments has one serial name for two shapes, and is refused too.

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

`= …` marks an optional element, one with a default. The defaults themselves are not in a
descriptor and need not be: the payload format encodes defaults, so a sender always sends every
field, and a receiver's default only fills in a field its peer did not know.

**When the revision must change.** `dumpWireSchema` compares the new schema with the committed one.
Messages are matched by serial name, which is what the wire carries. From each message the two
schemas are walked side by side, so renaming a Kotlin class that is not polymorphic changes the
dump's text but not the verdict. If a change is breaking and `protocolRevision` still equals the
dump's `revision`, the task fails, lists each breaking change with its path, and names the revision
to set. A raised revision lets any change through; a lowered one fails.

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
| Make an optional field required | breaking | |
| Change a field's type, or its nullability either way | breaking | |
| Rename a field's serial name | breaking | One required field removed and one added |
| Add or remove an enum entry | breaking | An unknown entry does not decode: `coerceInputValues` is off |
| Add or remove a sealed subtype, or change the discriminator | breaking | |
| Add an event | additive | The older peer skips it |
| Add a request | additive | The older peer answers with a failure, which `request` throws |
| Remove a message, or change its serial name | breaking | Messages are matched by serial name |
| Turn an event into a request or back, or change a reply type | breaking | |
| Rename a Kotlin class that is not polymorphic, reorder fields, edit an `@McpDescription` | no wire change | |

What the check cannot know, and says in its output:

- **Direction.** The schema does not say which side sends a message, so an enum entry that only the
  agent sends counts as breaking both ways. The host's revision range is how a plugin keeps serving
  older agents across such a change (next section).
- **Meaning.** A field whose meaning changes, a default that now means something else, or a custom
  serializer that writes its string differently: all of them still decode. Their author raises the
  revision by hand.
- **Requests within one revision.** An older peer at the same revision may lack a newer request.
  The caller catches `JetWhaleRequestException` and degrades ([open decision 1](#open-decisions)).

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
  that knows it decodes an older agent's tree. A break the host sends needs the instance to know
  its agent's revision, which phase 6 adds as `agentProtocolRevision`.
- `agentVersionRange` keeps its meaning, for agents that send no revision.

The agent sends one revision and does not negotiate down. An app pins its agent, and the host is the
side a developer can update in one click, so the range belongs to the host.

### The rule

One rule decides, in negotiation and in #320's binding:

```
accepts(manifest, agentPlugin):
    if agentPlugin.protocolRevision != null and manifest.protocolRevisionRange != null:
        return agentPlugin.protocolRevision in manifest.protocolRevisionRange
    // one side predates revisions
    return manifest.agentVersionRange?.contains(agentPlugin.pluginVersion) ?: true
```

The range compares with #320's `PluginVersionOrder`: release parts as numbers, a release after its
pre-releases, pre-release numbers as numbers, so `1.0.0-alpha9` < `1.0.0-alpha10` < `1.0.0`. Bounds
use the full order, so `1.3.0-beta1` is below a range that starts at `1.3.0`. `HostVersion` in
`:jetwhale-host:release-metadata` is not reused: it accepts only JetWhale's own tag format, and a
third-party version can be anything.

### The build writes the release version

- **Host.** The host Gradle plugin generates the manifest that goes into the jar. It writes
  `version` from `project.version`, and `protocolRevisionRange.max` from the contract with the same
  `pluginId` on the module's runtime classpath. Everything else stays as written. The build fails
  when the source manifest has a `version` or a `max` that disagrees, when `project.version` is
  unspecified, and when a contract on the classpath matches no entry's `pluginId`, which is a typo
  on one side. An entry with `requiresAgent: true` and no contract gets no range and a warning: it
  is matched by `agentVersionRange`, as today.
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
3. Delete `version` from the source manifest. Keep `agentVersionRange` for as long as agents without
   a revision should be served.

Upgrading only `com.kitakkun.jetwhale.host` already writes `version`. Without step 1 the plugin is
matched by its version range, as before.

**Plugins built before this keep working.** A jar whose manifest has no revision range is matched by
its `agentVersionRange`, and so is an agent that sends no revision. Only the comparison changes, to
the pre-release-aware order.

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
from the same schema, and changes on any change: an additive one, and even a Kotlin rename that the
wire never sees. Negotiation needs the first, #436's strict decoding the second, and one dump gives
both.

### What #320 changes

1. **Order.** Identity stays `(pluginId, version)` and the order stays `PluginVersionOrder`; the
   version becomes the release version. `PluginVersionOrder` puts `X-SNAPSHOT` before `X`: it now
   ranks `1.0.0-alpha14-SNAPSHOT` after `1.0.0-alpha14`, because the suffix reads as one more
   pre-release segment.
2. **Binding.** `AgentVersionCompatibility(agentVersion)` becomes the rule above and takes the
   agent's `JetWhalePluginInfo`. "The newest version whose `agentVersionRange` accepts the agent"
   becomes "the newest version that accepts the agent", by revision when both sides have one.
   `compareReleases` goes, since bounds use the full order.
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
- The manifest gains `protocolRevisionRange` with integer `min` and `max`. Released hosts ignore it.
  A jar's manifest still has `version`, which released hosts require; the source may leave it out.
  `schemas/plugin-manifest.schema.json`, which allows no other properties (`:8`, `:17`), adds the
  range and makes `version` optional, since it describes the source.
- The protocol jar carries `META-INF/jetwhale/protocols/<pluginId>.json` with `pluginId`,
  `releaseVersion`, `protocolRevision` and `wireSchemaHash`. The build reads it; the host does not.

**API.**

- `jetwhale-protocol-core`: `JetWhalePluginContract`.
- `jetwhale-agent-sdk`, **Breaking**: `JetWhaleAgentPlugin.contract` replaces `pluginId` and
  `pluginVersion`.
- `jetwhale-host-sdk`: `JetWhaleHostPluginManifest.protocolRevisionRange`; in phase 6,
  `agentProtocolRevision` on `JetWhaleMessagingHostPlugin`.
- Gradle: `com.kitakkun.jetwhale.protocol`, whose `jetwhaleProtocol` extension has `pluginId`,
  `protocolRevision` and `contractQualifiedName`, with `dumpWireSchema` and `checkWireSchema`.
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
- When an official plugin later raises `min` above 1, its `agentVersionRange` goes in the same
  change, since an agent without a revision speaks revision 1 at best.
- A raised revision is a **Breaking** changelog entry that says to update the host and the agent
  together, as `agents/release.md` already requires for such a wire change.

### Every combination

H0 is a host released before this, H1 one after. J0 is a host plugin jar whose manifest has no
revision range, J1 one built with the new Gradle plugins. A0 is an agent that sends no revision and
a hand-written version, A1 one that sends a contract. An H0 host compares release numbers only:
dotted numbers on `main`, release parts with #320. Before #320 it reports the verdict without
enforcing it (see [Today](#what-follows-from-it)); with #320 it enforces it.

| Host | Jar | Agent | Decided by | Outcome |
|---|---|---|---|---|
| H0 | J0 | A0 | release numbers against `agentVersionRange` | As today |
| H0 | J0 | A1 | release numbers against the release version | `1.0.0-alpha15` counts as `1.0.0`: network, nav3, storage, actions and example agents stay compatible until the release version passes 1.0.0, and semantics agents are incompatible. Update the host |
| H0 | J1 | A0 | the kept `agentVersionRange` | As today |
| H0 | J1 | A1 | as H0, J0, A1 | As H0, J0, A1 |
| H1 | J0 | A0 | the pre-release-aware range | As today, except that versions compare correctly: a pre-release sorts below its release, and `1.2.3-beta1` keeps its patch number |
| H1 | J0 | A1 | the pre-release-aware range against the release version | A jar built before this sees release versions in its author's range |
| H1 | J1 | A0 | the kept `agentVersionRange`; none accepts any | As today |
| H1 | J1 | A1 | the revision against the range | The design |

H0 rows cannot change. Only H0, J0, A1 is worse than today, and only for semantics agents. Phases 1
and 4 ship in one release, whose notes say to update the host first; the host updates itself in one
click since 1.0.0-alpha13. An H0 host installs official jars of its own version
(`OfficialPluginRelease.kt:21-33`), so it rarely meets J1.

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
    and `NodeAction` gained `BringIntoView` and `ScrollToIndex`. Additive: three new requests and two
    optional fields. #271's manual move to 1.1.0 landed in the same release and covered these too.
  - Every other step: no change.
- **One change at a time.** Nineteen single edits of today's network or semantics protocol, each
  classified as the table says:
  - additive: an optional field added, an optional field removed, a required field made optional,
    an event added, a request added;
  - breaking: a required field added, a required field removed, a type changed, a field made
    nullable, an optional field made required, an enum entry added, a message renamed, a field
    renamed, a reply type changed, an event turned into a request, a sealed subtype added;
  - no wire change: fields reordered and an `@McpDescription` edited, both with the same dump and
    hash; `CapturedHttpRequest` renamed in Kotlin, with a new dump text and hash.

  With the revision raised to 2, the sealed-subtype change passed.

Not tried: the Gradle wiring (worker isolation, the configuration cache), manifest generation, and
anything at runtime.

## Testing

- **The rule.** A table test with one case per H1 row of the matrix: `null` revisions on either
  side, a missing range, bounds at pre-releases and snapshots.
- **Order.** `PluginVersionOrder` with a snapshot before its release, added to #320's cases.
- **Wire.** Serialization tests that decode the JSON a 1.0.0-alpha13 agent sends into the new
  `JetWhalePluginInfo`, and the new JSON into a class shaped like the old one; the same for the
  manifest.
- **Reader and classifier.** One unit test per row of the classification table, from fixtures like
  the ones above, and the network alpha11 and alpha12 dumps as a regression case.
- **Gradle.** TestKit tests of `com.kitakkun.jetwhale.protocol` on a small KMP fixture (generate,
  dump, check, refuse a break without a new revision, refuse open polymorphism) and of the host
  plugin's manifest (version written, a mismatch fails, `max` from a contract jar, a warning without
  one), with the configuration cache on.
- **This repository.** `checkWireSchema` runs in `check` for every official protocol module. PR CI
  also compares each dump with its version on the base branch, which catches a dump edited by hand.
- **By hand.** A sandboxed host with two versions of the example plugin, at revisions 1 and 2 with
  `min` 1, and QA agents at revisions 1, 2 and 3 and without one.

## Risks

- **A dump edited by hand passes `checkWireSchema`.** The check proves the dump is current, not that
  the previous one was respected. Here the base-branch comparison closes the gap; elsewhere it is a
  diff in review.
- **Verdicts are conservative.** A break in a direction nobody uses still asks for a new revision.
  The cost is one number, and a host `min` that keeps serving older agents when the author knows
  better.
- **The build runs the module's classes.** Reading serializers initializes protocol classes inside a
  Gradle worker. Data classes do nothing there; process isolation contains anything else.
- **Storage grows** under #320, one directory per release, until its item 3 lands.
- **Released hosts report new semantics agents as incompatible** (the matrix). The release notes say
  to update the host first.

## Open decisions

1. **Is a new request additive or breaking?** Recommendation: additive. Breaking would cut off every
   older agent to add one request, and a caller already has to handle a failed request.
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

## Phased PRs

1. **Host and wire.** The `JetWhalePluginInfo` fields, the manifest range and its schema, the rule in
   negotiation and in #320's binding, the pre-release-aware fallback, the verdict on the plugin page
   and in MCP, the agent's log line, and the protocol reference. Tested with manifests and infos
   built by hand; nothing produces revisions yet.
2. **`com.kitakkun.jetwhale.protocol`.** `JetWhalePluginContract`, the generated contract, the jar
   resource, `dumpWireSchema` and `checkWireSchema`, applied to network; then the other official
   protocol modules at revision 1, and the base-branch comparison in CI.
3. **Manifest generation** in `com.kitakkun.jetwhale.host`. Official manifests drop `version`.
4. **Agent SDK, Breaking.** `contract`, sent by the runtime; the official agents and the QA agent;
   `runJetWhaleQaAgent` passing the revision; the plugin guide's opt-in steps.
5. **The install warning** for a manifest version that differs from the coordinates.
6. **When a plugin first needs it:** `agentProtocolRevision` on the host plugin instance.

#320 lands before 1. Phases 1 and 2 are independent of each other; 3 and 4 need 2; 1 and 4 ship in
the same release. #436's Swift generator builds on 2. Each phase updates the guides it changes.
