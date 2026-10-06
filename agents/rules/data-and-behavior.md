# Data and Behavior Rules

A type is either data or an object with behavior, never both. Data holds values. An object with
behavior holds what it works with, and does the work through its own members.

## Data

- **Data is a `data class` or a `@Serializable` class that holds values.** It has no I/O and no
  dependencies, and it doesn't call out to anything.
- **Data may compute about its own shape.** Its functions are pure, and they change only when its
  shape does. That covers:
  - parsing and formatting its own representation: `HostVersion.parse(text)`, `version.name`;
  - a copy with some values changed: `state.copy(selected = id)`;
  - a question about its values, or a comparison: `NodeBounds.isEmpty`, `version > other`.
- **The line is effects.** A function that reads or writes files, uses the network, the clock,
  randomness or shared state, or takes a dependency, is behavior. It doesn't go on data, however
  small it is. `platformRelease.check(jar)` reads and hashes a 120 MB jar, so it is behavior.
- **A type that mirrors a file or a message is data.** `InstanceJson` holds the contents of
  `instance.json`: port, pid and token. Reading, writing and using them happen elsewhere.

## Objects with behavior

- **The constructor takes what the work needs:** a directory, a client, a lock, a clock. Callers
  hold the object and tell it what to do.
  - `bringToFrontClient.requestBringToFront(timeout)`, not `InstanceJson.requestBringToFront(directory, timeout)`.
  - `hostVersionsDirectory.readInstanceJson()`, not `InstanceJson.read(directory)`.
- **Work on a file or directory belongs to the type that owns it.** `HostVersionsDirectory` reads
  and writes `launcher-state.json` and `instance.json`.
- **A protocol is an object of its own,** named after what it does (`BringToFrontClient`), holding
  the format both ends share.

## Conversions between types

- **A conversion lives where both types are visible.** That is an extension in the layer that knows
  both, named after its result: `dto.toDomain()`, `state.toUiModel()`. Don't write
  `Domain.from(dto)` on the target's companion: the type would then depend on the other layer.
- **A conversion that needs a dependency is behavior,** such as resources, formatting settings or a
  clock. It goes in a class that holds them.

## Policies

- **A policy gets a class named after it, even when it is pure.** A policy is a decision that
  changes for reasons other than the data's shape: what can run, what to keep, what to hide. Write
  `LauncherCompatibility(capabilities).refusalOf(metadata)`, not
  `metadata.refusalOn(capabilities)`. The rule then has one home to test and to change.
- **Test:** if it changes when the data's shape changes, it stays with the data. If it changes for
  a rule, a setting or another layer, it goes outside.

## Not allowed

- **Behavior added to data:** I/O, network calls, protocols or dependencies, whether in its members
  or in its companion. The data type then changes for reasons that have nothing to do with its
  values. Its calls also read as if the data did the work.
- **Static-style functions that take the data and the dependencies as parameters,** in a companion,
  a top-level function or an `object`. Make them members of an object that holds the dependencies.
