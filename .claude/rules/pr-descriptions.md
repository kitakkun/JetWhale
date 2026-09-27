# Pull Request Description Rules

A description tells a reviewer what the PR is for and where to look. It is not a list of what was
done: the diff and the commits already say that. Keep it short enough to read in about thirty
seconds.

## What to write

1. **The goal.** One or two sentences: the problem, and what is different for the user or the
   developer once this lands.
2. **The key points.** The decisions that shape the change: the approach taken, and why this one
   rather than the obvious alternative. A few bullets at most.
3. **Where a reviewer should look.** Implementation points a reviewer would want to check or would
   otherwise stumble on: a concurrency or lifecycle assumption, a trade-off, a behavior that looks
   wrong but is deliberate, a part that is easy to miss in a large diff.
4. **Notes, only when they exist.** Breaking changes, experimental status, dependencies and merge
   order, what is deliberately left for later.
5. **Verification, in a line or two.** What kind of checking was done, not a log of it.

## What not to write

- A changelog of the PR: "added X, renamed Y, moved Z" with no reason attached.
- Review history: which round fixed what, which reviewer pointed out what.
- File inventories, per-commit narration, test lists, mutation-check logs.
- Details of the local environment: machine names, paths, plugins or apps installed on someone's
  machine.

## Keep

- Issue links (`Closes #N`), screenshots and recordings, stacked-PR and base notes.

## Keep it current

When a PR is reworked, rewrite the description to match what the PR now does instead of appending
to it.
