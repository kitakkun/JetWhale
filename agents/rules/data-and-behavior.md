# Data and Behavior Rules

A type is either data or an object with behavior, never both. Data holds values. An object with
behavior holds what it works with, and does the work through its own members.

## Data

- **Data is a `data class` or a `@Serializable` class that only holds values.** It has no I/O and
  no dependencies, and it doesn't call out to anything.
- **Its functions derive only from its own values:** a computed property, a predicate such as
  `NodeBounds.isEmpty`, or a factory on its companion (see `function-placement.md`, rule 5).
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

## Not allowed

- **Behavior added to data:** I/O, network calls, protocols or dependencies, whether in its members
  or in its companion. The data type then changes for reasons that have nothing to do with its
  values. Its calls also read as if the data did the work.
- **Static-style functions that take the data and the dependencies as parameters,** in a companion,
  a top-level function or an `object`. Make them members of an object that holds the dependencies.
