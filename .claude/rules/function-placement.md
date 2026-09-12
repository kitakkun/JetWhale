# Function Placement Rules

Put a function where its readers will look for it: next to its use, with no more visibility than
its use needs. A reviewer reads the call site and the definition together; a helper that lives in
another file, or is reachable from places that never call it, breaks that reading.

This is about implementation helpers. A public API surface (`jetwhale-host-ui`, the protocol and
SDK modules) is designed for its callers outside the module, and its extensions stay public.

## Where a function goes

1. **Used in one class, object, or top-level function** — a `private` member of that class or
   object, or a `private` top-level function right after that top-level caller.
2. **Used in one file, by more than one declaration** — a `private` top-level function in that
   file, placed after its last caller.
3. **Used by a few classes that share a context** (a root view, a host callback, a UI thread, a
   configuration) — a class *named after that context*. The shared dependencies are its
   constructor parameters, the operations the callers need are its members, and the helpers behind
   them are `private` to it. The callers then hold that class instead of reaching for scattered
   functions; the class name says which context the operations belong to. A parameterless
   `object` fits only when there is no shared dependency to hold.
4. **Meaningful for the receiver alone, anywhere** — a top-level extension function, with the
   narrowest visibility its callers allow. `View.isScrollable()` qualifies: it derives from the
   receiver's public state and means the same thing from every call site. A function that needs
   anything beyond the receiver to make sense does not qualify and belongs under 1–3.
5. **A factory or predicate for a type** — with that type, in the file named for it. A predicate
   is an extension of the type (`NodeBounds.isEmpty`). A factory has no instance to extend, so it
   goes on the type's companion (`NodeActionResult.Companion.missingArgument(...)`; a
   `@Serializable` class already has one) or, when the type has no companion, is a top-level
   function in that file named for the type it builds. A reader looking for "how do I build one of
   these" finds it by the type's name.

## Not allowed

- An `internal` or public top-level helper whose callers are one or two distant classes. Move it to
  those callers (1–3), or show it is generic (4–5).
- A "namespace" object with no shared context — a bag of unrelated functions. If they share
  nothing, they belong at their call sites; if they share a context, name it (3).
- A helper placed in an interface's file for implementations that live in other files. The
  interface file defines the contract; helpers for implementing it go with the implementations, or
  on the type they produce (5). Implementations declared in the same file as their interface follow
  rule 2 as usual.

## Not worth a function

A function has to do something its call site cannot say in one line. It is not worth existing when:

- Its body is a single call that passes the receiver and arguments straight through:
  `private fun EditText.focusForEditing() { requestFocus() }` is `requestFocus()` under another
  name. Call the original. If the reason for the call is not obvious, that reason is a comment at
  the call site, not a function name.
- Its body is one expression used at one call site. Write the expression there.

Extract a function when the body has a shape worth naming — a computation, a branch, a sequence —
or when the same non-trivial body appears at several call sites. Naming a single forwarding call
adds a hop for the reader and nothing for the code.

## Why

A function reachable from more places than use it is a question at every call site: "who else
calls this, and can I change it?" Keeping visibility equal to use answers that in the signature.
