# Function Placement Rules

Put a function where its readers will look for it: next to its use, with no more visibility than
its use needs. A reviewer reads the call site and the definition together; a helper that lives in
another file, or is reachable from places that never call it, breaks that reading.

## Where a function goes

1. **Used in one class or object** — a `private` member of that class or object.
2. **Used in one file, by more than one class** — a `private` top-level function in that file,
   placed after its last caller.
3. **Used by a few classes that share a context** (a root view, a host callback, a UI thread, a
   configuration) — a class or object *named after that context*, holding the shared dependencies
   as constructor parameters and the helpers as `private` members. The class name says which
   context the functions belong to; nothing outside it can reach them.
4. **Meaningful for the receiver alone, anywhere** — a top-level extension function, `internal` at
   most. `View.isScrollable()` qualifies: it derives from the receiver's public state and means the
   same thing from every call site. A function that needs anything beyond the receiver to make
   sense does not qualify and belongs under 1–3.
5. **A factory or predicate for a type** (a `NodeActionResult` for a missing argument, say) — on
   that type: an extension of the type or its companion, in the file named for the type. A reader
   looking for "how do I build one of these" finds it by the type's name.

## Not allowed

- An `internal` or public top-level helper whose callers are one or two distant classes. Move it to
  those callers (1–3), or show it is generic (4–5).
- A "namespace" object with no shared context — a bag of unrelated functions. If they share
  nothing, they belong at their call sites; if they share a context, name it (3).
- A helper placed in an interface's file because the interface's implementations use it. The
  interface file defines the contract; helpers for implementing it go with the implementations, or
  on the type they produce (5).

## Why

A function reachable from more places than use it is a question at every call site: "who else
calls this, and can I change it?" Keeping visibility equal to use answers that in the signature.
