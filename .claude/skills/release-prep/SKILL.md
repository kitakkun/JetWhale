---
name: release-prep
description: Prepare a JetWhale release — draft the changelog entry and open the release-prep PR. Use when asked to prepare, cut or plan a release, or to write the changelog for a version.
---

# Release prep

Follow `agents/release.md`. It holds the procedure and the changelog criteria; this skill only adds
how to run it.

1. Take the version from the request, and the previous release tag from
   `git tag --sort=-creatordate | grep -v -- '-SNAPSHOT$' | head -1`; the Publish Snapshot workflow
   creates `<version>-SNAPSHOT` tags, which are not releases.
2. Read every PR merged since the previous tag and draft the `CHANGELOG.md` section by the criteria
   in `agents/release.md`. List the PRs you left out, and any entry whose Breaking status you were
   unsure of.
3. Open the release-prep PR with the section, the fresh `[Unreleased]`, the updated links and the
   version check, and ask the maintainer to review it.
4. Never push the tag without the maintainer's explicit go-ahead. Once they give it, tag the
   release-prep PR's merge commit and watch the Publish and Distribute Desktop Application runs.
