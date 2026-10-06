# Naming Rules

Name a function after what it does. Someone reading a call should know its effect without opening
the function.

A name that states a role, an intent or a moment, rather than the effect, is the anti-pattern this
rule exists for. `publish()`, `markReady()` and `finish()` tell a reader why or when something runs.
They don't say what it changes, so every reader opens the body. The body can then change while the
name still seems to fit.

## Functions

- **Name the action and what it acts on:** `publishInstanceJson()`, `deleteStagingDirectory()`,
  `requestBringToFront()`. The reason it is called goes in KDoc, not in the name.
- **Avoid verbs that would fit any body.** These include `handle`, `process`, `manage`, `perform`,
  `execute`, `do`, `apply`, `sync`, `prepare` and `setUp`. Use `run`, `update`, `resolve` or
  `check` only with an object that makes them specific: `runHost`, `updateWindowTitle`,
  `verifyJarHash`, `throwIfClosed`.
- **Let a long name be long.** If the honest name needs "and", the function probably does two
  things: split it, or keep the long name.
- **Make a predicate a question about what it checks:** `isInstanceJsonPublishedBy(pid)`, not
  `isPublishedBy(pid)`.
- **Framework conventions are exempt:** `main`, `onClick`, `onDispose`, Compose `Content`, and
  overrides of an interface you don't own. The convention already says what calls them.

## Values and types

- **Name a value after what it holds**, which is usually its type: `processTable: ProcessTable`,
  `hostVersionsRepository: HostVersionsRepository`. Not `processes` or `versions`.
- **Name a type that holds a file's contents after the file:** `InstanceJson` for `instance.json`.
- **Give one concept one word everywhere.** A version's text is not `version` in one place and
  `name` in another.

## Words whose meaning depends on context

- **Add a qualifying word, even when it looks redundant.** Some words mean different things in
  different parts of this codebase. A name built on one bare is read in whichever sense the reader
  meets first.
- **Write the qualified form:**
  - `startupTimeWindow`, not `startupWindow`, in an app where "window" is a GUI window;
  - `instanceJsonFile`, not `record`;
  - `processTable`, not `processes`.
- **Watch these words in particular:** window, session, state, record, plugin, host, agent, version
  and name. Each has more than one meaning here. A name that reads unambiguously in isolation is
  worth the extra word.

## Renames that are not made in passing

- **Public API of a published module** (protocol, SDKs, `jetwhale-host-ui`). A rename breaks its
  callers, so it gets a PR of its own that says so. `jetwhale-host-ui` keeps the signatures it has
  shipped.
- **Wire-visible names:** protocol messages, JSON fields, file names and system properties. They are a
  contract between versions. Change them only before a release has shipped them.
