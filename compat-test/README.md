# Kotlin version compatibility test

A standalone Gradle build (not part of the root build) that compiles and runs a minimal consumer
app against published JetWhale artifacts, to verify the **minimum consumer Kotlin version**
documented in [Getting Started](../docs/guide/getting-started.md).

## Run the matrix

```sh
./run-matrix.sh 1.0.0-alpha07                # default matrix: Kotlin 2.0 / 2.1 / 2.2 / 2.3
./run-matrix.sh 1.0.0-alpha07 2.2.21 2.3.0   # explicit Kotlin versions
```

For each Kotlin version it compiles and runs `src/main/kotlin/Main.kt`. When a version fails
(metadata-version error), it retries with `-Xskip-metadata-version-check` to confirm the
documented workaround still works. The script exits non-zero only if the workaround also fails.

## Test a pre-release version

Publish to Maven Local from the repository root, then point the script at that version
(`mavenLocal()` is resolved first):

```sh
(cd .. && ./gradlew publishToMavenLocal)
./run-matrix.sh <version>
```

## Single invocation

```sh
../gradlew run -PkotlinVersion=2.3.0 -PjetwhaleVersion=1.0.0-alpha07
../gradlew run -PkotlinVersion=2.2.21 -PjetwhaleVersion=1.0.0-alpha07 -PskipMetadataCheck=true
```

Success prints `RUNTIME_OK`. No running host is required — the agent simply retries the
connection in the background.

## Expected results (as of `1.0.0-alpha07`, built with Kotlin 2.4.0)

| Consumer Kotlin | Plain | With `-Xskip-metadata-version-check` |
|---|---|---|
| 2.0.21 | ❌ | ✅ |
| 2.1.21 | ❌ | ✅ |
| 2.2.21 | ❌ | ✅ |
| 2.3.0 | ✅ | — |

The Kotlin compiler reads metadata up to one minor version ahead of itself, so the minimum
consumer Kotlin is one minor version below the Kotlin used to build the artifacts.
