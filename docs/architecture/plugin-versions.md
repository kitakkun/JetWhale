# Several versions of one host plugin

Status: implemented in `feat/plugin-multi-version` (stacked on #303).

## Why

An app pins the agent side of a plugin through its dependencies; the host picks up whatever host
jar is in the plugins directory. Two apps built against different agent versions of the same
plugin could not both be served: the host loaded one version per `pluginId`, and an app outside
that version's `agentVersionRange` was told the plugin is incompatible. This design lets several
versions of a host plugin be installed side by side and serves each app with a version that fits
it.

## Model

- **Loaded versions.** `PluginFactoryRepository` keeps every loaded version of each `pluginId`
  (`loadedPluginVersions`, newest first). Each version comes from its own jar and therefore its own
  classloader. `loadedPlugins` still answers "the newest version of each id", which is what the
  metadata, settings and MCP listing use.
- **Which jar replaces which.** A jar replaces another only when both provide the same
  `(pluginId, version)`. Different versions coexist. Overwriting a jar file in place is still a
  replacement of what that file provided.
- **Ordering.** Versions compare numerically dot by dot (`1.10.0` > `1.9.2`); a non-numeric
  component counts as 0, as `agentVersionRange` checks already did.

## Binding a session to a version

- **App sessions.** An agent advertises each plugin with its own version
  (`JetWhalePluginInfo.pluginVersion`). The host binds the **newest loaded version whose
  `agentVersionRange` includes it**. Negotiation uses the same rule: a plugin is reported
  incompatible only when no loaded version fits, and `availablePlugins` carries the bound host
  version.
- **The host session** (plugins that need no app) binds the newest version, and moves to a newer one
  as soon as it is installed: it lasts as long as the host, so keeping its version would hold the
  upgrade back until a restart.
- **An app's binding is kept in the instance.** `PluginInstanceService` records the version each
  `(sessionId, pluginId)` instance was created from. Reconciliation keeps an app's existing binding
  while that version stays loaded, so installing a newer version does not swap a running app's plugin
  underneath it; the app's next connection, or one whose bound version was removed, binds afresh. A
  reload of the same version (new classloader) recreates the instance from that version.
- Instances, NavKeys and screens stay keyed by `(sessionId, pluginId)`: the version is resolved per
  session, so nothing above the instance layer needs to know which version it talks to.

## Enabled state

Enabled is **per `pluginId`**, as before: enabling a plugin enables whichever version each session
binds. A version the user does not want is **removed** (its jar is unloaded and deleted), not
disabled. Per-version enablement would add a second switch whose only effect is a subset of
removal, and would make "why is this app not getting v2" harder to answer.

## Data

Each version gets its own storage: `plugin-data/<pluginId>/<version>/store.json`, with a
`metadata.json` beside it naming the version (the directory name is sanitized). Two versions that run
at once never write the same file (DataStore does not allow two writers on one file).

**Seeding.** The first time a version's storage is used and it has no data yet, it is seeded with a
copy of the nearest older version's data for the same `pluginId`: the highest version below it that
has a store. Data from before this change (`plugin-data/<pluginId>/store.json`) counts as older than
every version. So an upgrade keeps the user's settings; after the seed each version writes only its
own copy.

**Format changes.** The copy includes the storage version stamp that `JetWhaleHostPlugin` already
keeps (`storageVersion` / `onStorageMigrate`). A new version that bumped `storageVersion` therefore
migrates the seeded copy through its own `onStorageMigrate`, exactly as it would migrate data it wrote
itself; the older version's copy is untouched. No second version number or hook is added: a manifest
field would have to be kept in step with `storageVersion` by hand, and the existing hook already runs
at the right moment (the first storage access of the new version).

A version never seeds from a newer one (downgrading starts from the nearest older data, or empty).

## MCP

- `tools/list` shows each `pluginId.tool` once, with the definition from the **newest** version that
  registers it.
- A call is dispatched to the instance bound to the target session, so it runs that session's
  version. If that version does not have the tool, the call fails with an error naming the plugin,
  the session and the bound version. An argument error from an older version names the version too,
  since the listed schema may be the newer one's.

## New jars at runtime (#303's prompt)

A jar that arrives with a version of an already installed `pluginId` is offered as a second version:
"New version 1.3.0 of X — **Load alongside** · **Replace 1.2.0** · **Later**". Load alongside keeps
both; Replace loads the new jar and removes the jars of the older versions. A jar written over an
existing jar file is still an update of what that file provided.

## UI

- Settings → Plugins lists each plugin once with its installed versions, each removable.
- The sidebar shows a version badge on a plugin row only when more than one version of it is
  installed; the badge shows the version bound in the selected app (or the host session).

## Not done

- Choosing a version by hand for a session (pinning). The automatic rule covers apps built against
  different SDKs; manual pinning can be added on top of the same binding.
- Migrating data downwards (a newer store seeding an older version).
