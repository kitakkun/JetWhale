# Debug Actions — design

Most apps grow a hidden debug menu: sign in as a test user, reset onboarding, flip a flag. Each team
builds its own, it lives on the device, and an AI agent cannot reach it. Debug Actions makes that
menu a JetWhale plugin that any app can adopt without depending on its architecture, and that is
equally usable by a person in the host and by an agent over MCP.

## Goals

- **No assumptions about the app.** No DI framework, navigation library or state container is
  required; registering is a function call, and Compose support is a separate artifact.
- **Declare once, use twice.** One declaration produces the host's form and the agent's schema.
- **Actions follow the UI.** A screen offers its own actions only while it is shown.
- **Safe by default.** Destructive actions need an explicit confirmation from people and agents.

## Shape

```
app ── register { action<A>(title) { run { … } } } ──▶ agent registry ──▶ ListActions / ActionsChanged
                                                                        ◀── RunAction(runId, id, args)
host ── form from ActionParameter list ── MCP listActions / runAction
```

### Arguments come from a serializer

An action's arguments are one `@Serializable` class. The agent turns the class's descriptor into a
list of `ActionParameter`s — name, input type, optional (has a default), nullable, description from
`@McpDescription`, enum values. The host builds its form from that list, and `listActions` turns
the same list into a JSON Schema. The agent decodes a run's arguments with the same serializer, so
what the form produces and what the action receives cannot drift apart.

Input types are deliberately few: string, integer, number, boolean, enum, and JSON for anything
else. A list or a nested class is entered as JSON rather than through a generated sub-form; that
keeps the form predictable and covers every type.

Values that depend on the app's state — test accounts, feature names — come from `options`
providers the host calls when an action is selected.

### Ids

An action's id is its group path and title (`Account / Sign in as test user`). A second
registration of the same path, e.g. a screen shown twice, gets `#2`. Ids are stable while an action
is registered, which is what a run needs; they are not persisted.

### Scoped registrations

`register { }` adds actions for good; `registerScoped { }` marks them as belonging to part of the
UI, and returns a registration to remove them. The Compose artifact's `DebugActions` ties that
registration to a `DisposableEffect`, restarted when its keys change. The agent pushes
`ActionsChanged` whenever the set changes, so the host follows the app's navigation.

### Runs

A run decodes its arguments, applies the action's timeout, optionally hops to the main thread,
and returns an `ActionResult`: outcome (success, failure, timeout, cancelled), the returned value as
text or JSON, and on failure the message and stack trace. Runs live in the plugin's activation
scope, so disabling the plugin cancels them; `CancelActionRun` cancels one by its run id.

### MCP

An MCP connection's tool list is fixed when it opens, while screen actions come and go. Actions
are therefore not tools of their own. There are two stable tools: `listActions`, which returns
every action with its argument schema and current suggested values, and `runAction`, which runs
one by id. A destructive action is refused unless the call passes `confirmDestructive: true`.
Runs an agent makes appear in the host's history marked as the agent's.

## Built-in actions

`platformBuiltInActions()` adds actions that need no app code and are safe on any app of the
platform: on Android, restart, deep link, per-app dark mode (12+) and language (13+); on iOS,
macOS and the JVM, opening a URL. The web has none: a page may open a URL only in response to a
user gesture.

Considered and left out: clearing app data (it kills the process and the session with it, and the
Storage Inspector already deletes what needs deleting), toggling animations or font scale
(system-wide settings an app cannot change for itself without a permission it should not hold),
and simulating push notifications (there is no generic way without the app's own handling code).

## Not in the first version

- Sub-forms for nested classes and lists (JSON covers them).
- Streaming progress from a long-running action.
- Grouping of options per parameter beyond a flat list.
- An annotation-driven declaration through the agent compiler plugin; the DSL is enough until
  registering by hand turns out to be a burden.
