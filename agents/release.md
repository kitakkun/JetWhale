# Release Preparation

How a JetWhale release is prepared: the changelog entry, the release-prep PR, and the tag. Read it
only when preparing a release.

## Finding what goes in

The previous tag is the newest release tag, not a `<version>-SNAPSHOT` tag (the Publish Snapshot
workflow creates those):

```shell
git tag --sort=-creatordate | grep -v -- '-SNAPSHOT$' | head -1
```

List what reached `main` since then, and read each pull request with its diff:

```shell
gh pr list --state merged --base main --search "merged:>=<prev-tag date>" --limit 200
git log --first-parent --format='%h %s' <prev-tag>..origin/main
gh pr view <N>
gh pr diff <N>
```

The repository allows merge, squash and rebase merges, so the pull request list is the inventory;
the first-parent log shows what the tag actually contains and catches commits pushed without a pull
request. Judge each change by its description and diff, not its title alone.

## What belongs in the changelog

The changelog is for people who use JetWhale: app developers who add the agent, and plugin
developers who build on the SDKs.

- **Include** what they notice: new plugins and features, changed behavior or UI, fixed bugs they
  could hit, API and wire-format changes.
- **Leave out** internal refactors, CI, lint and Kotrail work, agent rules, and docs-only changes,
  unless they change what users see.

## Breaking changes

Mark an entry **Breaking** when a user has to act:

- a public API or MCP tool, parameter or output is removed or renamed, or changes meaning;
- a wire-format change means the host and the agent must be updated together;
- an older host or agent fails, or loses data, against the new one.

A Breaking entry says what to do: which call to use instead, or which side to update.

## Wording

- One line per entry, written from the user's point of view: what they can now do, or what no
  longer goes wrong.
- End each entry with its PR links, `(#N)`. Fold related PRs into one line.
- Mark experimental features as such (the MCP server, Debug Actions, Device Mirror).
- Say "not published; build from source" for anything not distributed yet (Device Mirror, the
  IntelliJ IDEA plugin).
- Group entries under Added, Changed, Fixed and Removed, following Keep a Changelog.

## The release-prep PR

One PR, merged right before tagging:

1. Move the `[Unreleased]` entries into `## [<version>] - <YYYY-MM-DD>`, or write the section from
   the merged PRs.
2. Add a fresh, empty `## [Unreleased]` above it.
3. Update the links at the bottom: `[Unreleased]` compares `<version>...HEAD`, and `[<version>]`
   compares `<prev-tag>...<version>`.
4. Check that `jetwhale` in `gradle/libs.versions.toml` equals `<version>`.
5. Check the section extracts: `.github/scripts/changelog-section.sh <version>`.

## Tagging

Tag the merge commit of the release-prep PR and push the tag:

```shell
git tag -a <version> -m "<version>" <merge-commit>
git push origin <version>
```

The tag starts two workflows:

- **Publish** releases the SDKs and the Gradle plugins to Maven Central.
- **Distribute Desktop Application** builds the host installers and creates a draft GitHub release.
  Its notes are the changelog section, followed by GitHub's list of merged PRs. It fails when the
  changelog has no section for the tag.

The maintainer checks the draft release and publishes it.

## After the release

Bump `jetwhale` in `gradle/libs.versions.toml` to the next version in a
`chore: bump version to <next>` commit. Snapshots of the next version are published as
`<next>-SNAPSHOT` from the Publish Snapshot workflow.
