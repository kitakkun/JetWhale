# Pull Request Description Rules

A reviewer should get the point of a PR in about thirty seconds. The description says what changes
and why; the diff says how. Aim for about 1,200 characters, and no more than about 2,500 for a
large feature.

## Shape

```
<One or two sentences: what changes for the user or developer, and why.>

## Changes
- Three to seven one-line bullets: the behavior and design points worth knowing before reading the diff.

## Notes
- Breaking changes, experimental status, dependencies and merge order, follow-ups.

## Verification
- One to three lines: what was run or tried.
```

Leave out a section that has nothing real to say.

## Keep

- Issue links (`Closes #N`), screenshots and recordings.
- Stacked-PR and base notes, breaking-change notes, experimental status.
- Out-of-scope items a reviewer would otherwise ask about.

## Leave out

- Review history: which round fixed what, which reviewer pointed out what. That lives in the review
  threads and in git.
- Per-commit narration, file inventories, and walk-throughs a reviewer can read in the diff.
- Long test lists and mutation-check logs; say what kind of checking was done in a line.
- Details of the local environment: machine names, paths, plugins or apps installed on someone's
  machine.

## Keep it current

When a PR is reworked, rewrite the description to match the current diff instead of appending to it.
