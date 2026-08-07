# Releasing

A release publishes only the artifacts whose sources changed. Maven Central bills publishers on
monthly file count, release size and release count (three-month averages), and file count is what
binds here: a Kotlin Multiplatform module publishes one Maven module per target, so the 11
multiplatform artifacts alone account for roughly 2,500 of the ~2,700 files a full release uploads —
most of them belonging to artifacts that did not change.

`gradle/published-versions.properties` records the version each artifact currently resolves to.
Artifacts that did not change keep an older version; `jetwhale-bom` and `jetwhale-catalog` map
consumers from the release version onto that combination.

## Steps

1. **Bump the release version.** `jetwhale` in `gradle/libs.versions.toml` is the single source of
   truth for it — it is the git tag, the BOM and catalog version, and the host app version. On a
   Kotlin or Compose bump, work through `.claude/skills/bump-dependencies/SKILL.md` first.

2. **Preview the plan.**

   ```sh
   ./gradlew printReleasePlan
   ```

   Nothing is written. Sanity-check the `publish` / `keep` split against what you actually changed.

3. **Record the plan.**

   ```sh
   ./gradlew prepareRelease
   ```

   Rewrites `gradle/published-versions.properties`: changed artifacts move to the release version,
   unchanged ones keep theirs, and `previous.train.version` becomes the version being released.

4. **Review the diff and commit it.** The recorded versions are what the release actually publishes,
   so the diff is the release's contents.

5. **Tag and push the tag.** `.github/workflows/publish.yaml` runs `verifyReleasePlan`, then
   `publishChangedToMavenCentral` once per build: the root build, then the `jetwhale-gradle-plugin`
   and `jetwhale-agent-plugin` included builds, whose tasks the root build cannot reach. Each build's
   task publishes only its own artifacts that move, and does nothing when none do. Every build that
   publishes something uploads one Central Portal deployment.

6. **Release the deployment** in the [Central Portal](https://central.sonatype.com/publishing/deployments) —
   `automaticRelease` is off, so uploads wait for a human.

7. **After the release:** add the tag to `docs-site/versions.json` on `main` (see
   `docs-site/README.md`), and run `compat-test/run-matrix.sh <version>` against the published
   artifacts to refresh the table in `compat-test/README.md`.

## How the changed set is worked out

`prepareRelease` diffs against the tag named by `previous.train.version` — a recorded value rather
than a `git describe` guess, because snapshot pre-releases also leave tags behind.

An artifact is republished when any of these holds:

- its project directory changed, or an ancestor project's own build script did (`:jetwhale-protocol`
  applies a `group` to its children);
- a **global input** changed: `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`,
  `gradle-conventions/`, `gradle/wrapper/`, or `gradle/libs.versions.toml` beyond the `jetwhale`
  line itself. A Kotlin or Compose bump changes klib metadata everywhere, so these force a full
  release;
- a published artifact it depends on moves, since its POM has to name the new version. This
  propagates to the leaves of the graph;
- it is always republished: `jetwhale-bom` and `jetwhale-catalog` describe the release, while the
  official plugins (`jetwhale-network-inspector`, `jetwhale-nav3-navigator`,
  `jetwhale-compose-semantics-inspector`) and `jetwhale-qa-agent` are resolved by the *host's*
  version rather than their own — through `OfficialPluginCatalog` and `runJetWhaleQaAgent`'s
  `qaAgentVersion` default — so a gap there is a 404 for users.

A new published module needs no wiring beyond applying the `publish` convention — `prepareRelease`
picks it up, and `verifyReleasePlan` fails the release while it is missing from the lock file, which
is also what stops the BOM and catalog from silently omitting it. Artifacts of the included builds
are the exception: they are listed by hand in `gradle-conventions/src/main/kotlin/release.gradle.kts`
because the root build cannot enumerate a separate build.

To republish something the diff considers unchanged, set its entry in
`gradle/published-versions.properties` to the release version by hand after `prepareRelease`.
Selection is simply "recorded version equals the release version".

## Snapshots

`.github/workflows/publish-snapshot.yaml` ignores the recorded versions: every artifact is published
at `<version>-SNAPSHOT`, so a snapshot is always a complete, self-consistent set. It uploads a full
release's worth of files each time, so run it deliberately rather than on every change.
