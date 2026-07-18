---
name: bump-dependencies
description: Bump JetWhale dependency versions in gradle/libs.versions.toml, including the Kotlin metadata-compatibility follow-ups (docs, README badge, compat-test) that are easy to forget.
---

# Bump dependencies

Procedure for bumping versions in `gradle/libs.versions.toml`. The version bump itself is the
easy part — the point of this skill is the follow-up work, especially around **Kotlin consumer
compatibility**.

## 1. Find latest versions

- Do NOT trust `search.maven.org` (solrsearch) — its index lags months behind. Fetch
  `maven-metadata.xml` directly:
  - Maven Central: `https://repo1.maven.org/maven2/<group-as-path>/<artifact>/maven-metadata.xml`
  - AndroidX / AGP: `https://dl.google.com/dl/android/maven2/<group-as-path>/<artifact>/maven-metadata.xml`
  - Gradle plugins not on Central: `https://plugins.gradle.org/m2/...`
- `<release>` may point at a pre-release; when ambiguous, list the `<version>` entries and pick
  by policy below.

## 2. Version selection policy

- Latest **stable** by default.
- Entries already on a pre-release line (e.g. `material3 = 1.x.0-alphaNN`) move to the latest of
  that same line; don't jump to a newer alpha line unless the paired stable deps move too.
- Keep `jetbrainsCompose` / `material3` / `navigation3` / `lifecycle` mutually consistent — they
  are one release train.
- `kotlinDsl` must match the Gradle wrapper version (`gradle/wrapper/gradle-wrapper.properties`),
  not the newest available.
- Compiler plugins (**KSP, Metro, Mokkery, Compose compiler**) are coupled to the Kotlin compiler
  version. When bumping Kotlin, check each plugin's release notes for its supported / minimum
  Kotlin before assuming the latest works.

## 3. Kotlin bump → consumer metadata compatibility (do not skip)

JetWhale publishes artifacts consumed by apps on *older* Kotlin versions. Kotlin metadata is
versioned by `major.minor`, and the compiler accepts metadata up to one minor ahead:

- **Patch bump** (e.g. 2.4.0 → 2.4.10): metadata version unchanged → the minimum consumer Kotlin
  (currently **2.3+**) does not change.
- **Minor/major bump** (e.g. 2.4.x → 2.5.x): metadata version changes → the minimum consumer
  Kotlin moves up. This is a breaking change for consumers; call it out in release notes.
- Older consumers can use `-Xskip-metadata-version-check` as an unofficial escape hatch — it is
  documented in `docs/guide/getting-started.md` but is unsupported and fragile (notably around
  `inline` functions).

On ANY Kotlin bump, update the hardcoded "built with" version:

- `docs/guide/getting-started.md` — the "currently **X.Y.Z**" in the Kotlin compatibility warning
  (and the stated minimum consumer Kotlin, if a minor bump moved it).
- `README.md` — the `Kotlin-X.Y.Z` badge.
- `docs/guide/developing-plugins.md` — no hardcoded version (it defers to release notes), but the
  next release notes MUST state the Kotlin (and Compose) version the host was built with, since
  plugins must be compiled with a matching Kotlin.
- `compat-test/README.md` — the expected-results table is per published release; leave past
  entries, but after the next release run `compat-test/run-matrix.sh <version>` against the
  published artifacts and record the new matrix.

## 4. Mechanical follow-ups

- AndroidX bumps may raise the required `compileSdk` (the build error says so). `compileSdk` is
  hardcoded in several places — update all of them:
  `grep -rn "compileSdk = " --include="*.kts" gradle-conventions demo jetwhale-agent-runtime jetwhale-plugins`
- JS/WASM lockfiles: if the build fails with "Lock file was changed", run
  `./gradlew kotlinUpgradeYarnLock kotlinWasmUpgradeYarnLock` and commit `kotlin-js-store/`.
- Fresh worktrees need `local.properties` with `sdk.dir=$HOME/Library/Android/sdk` (gitignored).

## 5. Verify

- `./gradlew build` and `./gradlew check` must both pass before committing.
- Major bumps (new major version of anything) deserve a skim of the library's changelog even if
  the build is green.
