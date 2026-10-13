# Comment Rules

Make the code say it. Write a comment only for what the code cannot carry.

A comment that restates what the next line does costs twice: it slows a reader who must check it
against the code, and it drifts out of date the moment the code changes. If that is all it would
say, leave it out.

## Before writing a comment

Try, in this order:

1. **Rename.** A name that states the intent (`isDebugStartFailed`, `MAX_TEMPLATE_DEPTH`) removes
   the need for the sentence.
2. **Restructure.** Extract the block into a function whose name is the comment; split a class whose
   sections needed banners.
3. **Let the test name speak.** Backtick test names carry the intent; do not repeat them above the
   assertions.

Only then write the comment.

## Worth a comment

- A constraint from outside the file: a platform or library behavior the call site cannot show
  (`NsdManager requires the trailing dot`, `save() buffers the whole body`).
- A deliberate trade-off, where the alternative is not obviously worse.
- Why something is *absent* — a call that is deliberately not made, an empty branch that is a
  decision rather than an omission.
- A wire-visible placeholder that looks dead but must stay.

## Not worth a comment

- Narration of the next statement.
- Section banners (`// ----- Public API -----`).
- `// do nothing` on an empty block: write `Unit`, or `{}`, and let it read as deliberate.
- History: what the code used to do, which bug a line fixed, what a review changed. That lives in
  git and in the PR.
- Anything a named constant, function or test name already says.

## KDoc

Public API keeps its KDoc — it is documentation for callers, not commentary. The same bar applies to
its prose: describe the contract, not the implementation line by line.
