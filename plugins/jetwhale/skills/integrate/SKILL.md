---
name: integrate
description: Add JetWhale to an app you want to debug — survey the project's build, HTTP client and DI framework, then wire startup and traffic capture behind a seam so no JetWhale symbol reaches release builds.
---

# Integrating JetWhale

Adding the dependency is three lines. Adding it *without* dragging a debugging tool into the
shipped binary is the actual job, and it is decided by two things you do not control: how the
project splits debug from release, and which DI framework it uses.

So this skill is a survey followed by a routing decision. Do not start editing until §1 and §2 are
answered — a wiring chosen for the wrong DI framework has to be undone before the right one goes
in, and the wrong dependency configuration is invisible until someone builds a release months
later.

**The rule everything else serves:** production code must never name a JetWhale type. Not in an
import, not in a signature, not behind an `if (DEBUG)`. Anything that names JetWhale needs JetWhale
on the classpath to compile, and a dependency you cannot remove from the release compile classpath
is a dependency you ship.

## 1. Survey the project

Run these before deciding anything. Each answer routes a later step.

```bash
# Targets: is this Android-only, or Kotlin Multiplatform?
grep -rlE 'kotlin\("multiplatform"\)|kotlin\.multiplatform|kotlin-multiplatform|kotlinMultiplatform' \
  --include='*.gradle.kts' --include='*.gradle' --include='*.toml' .

# DI framework
grep -rnE 'dev\.zacsweers\.metro|com\.squareup\.anvil|lastmile\.kotlin\.inject\.anvil|com\.google\.dagger|dagger\.hilt|io\.insert-koin' \
  --include='*.kts' --include='*.gradle' --include='*.toml' . | head

# HTTP client
grep -rnE 'io\.ktor:ktor-client|com\.squareup\.okhttp3' --include='*.kts' --include='*.gradle' --include='*.toml' . \
  | head

# An existing debug seam to ride on
grep -rn 'BuildConfig.DEBUG' --include='*.kt' . | head
find . -type d -name debug -path '*/src/*' | head
grep -rlniE 'class (NoOp|Noop)[A-Za-z]*' --include='*.kt' . | head
```

Answer these five, out loud, before continuing:

| Question | Why it routes |
|---|---|
| Android variants, KMP, or both? | Whether `debugImplementation` exists at all (§3) |
| Which DI framework? | Which reference file to follow (§4) |
| Ktor, OkHttp, both, or neither? | Whether the Network Inspector is in scope at all |
| Is there already a debug-only module or `src/debug` source set? | Ride it instead of inventing one (§2) |
| Where does the app start — `Application.onCreate()`, `main()`, an initializer list? | Where the one call goes (§4) |

**Kotlin version.** JetWhale needs **Kotlin 2.3+** in the consuming project; older versions fail
the build with metadata-version errors. Check it now (`grep -n 'kotlin' gradle/libs.versions.toml`)
— if the project is older, stop and say so. `-Xskip-metadata-version-check` exists but is an
unsupported escape hatch, not a plan.

**Targets.** JetWhale publishes `jvm`, `android`, `js`, `wasmJs`, `iosArm64`, `iosSimulatorArm64`,
`macosArm64`, `mingwX64`, `linuxX64` and `linuxArm64`: no `iosX64`, no `macosX64`, no watchOS or
tvOS. A KMP project that declares one of those cannot depend on JetWhale from `commonMain`, or from
any source set such a target compiles. Check the targets, including those a convention plugin
declares:

```bash
grep -rniE 'iosX64|macosX64|watchos|tvos' --include='*.gradle.kts' --include='*.gradle' --include='*.kt' . \
  | head
```

If any turn up, wire JetWhale only through source sets of supported targets, or stop and say so.

## 2. Ride an existing seam before building one

Most apps that already carry debug-only tooling — Chucker, Flipper, LeakCanary, an internal debug
drawer — have solved this problem once. Reuse costs nothing and matches what reviewers expect.

Look for, in order:

1. **A debug-only Gradle module** (`:debug`, `:core:debug-tooling`, anything pulled in with
   `debugImplementation`). Put the JetWhale wiring there and you are nearly done.
2. **An existing initializer abstraction** — an interface with a no-op implementation, an
   `AppInitializer` list, a `Set<Initializer>` multibinding. Contribute one more element.
3. **`src/debug` / `src/release` source sets** holding variant-specific implementations of a shared
   interface. Add your implementation to the debug side.

Only when none of these exist do you create the seam yourself, following the reference for the
project's DI framework.

**Do not introduce a DI framework to solve this.** A project with no seam and no container wants
the plain-interface version in `references/no-di.md`, not a new dependency in its production build.

## 3. Add the dependencies

```kotlin
dependencies {
    // `implementation` here only because this block belongs in a debug-only module.
    // In an Android app module it is `debugImplementation` — see the table below.
    // The always-present module that declares the seam this module implements
    // (`:core:debug` in the references):
    implementation(projects.core.debug)
    implementation("com.kitakkun.jetwhale:jetwhale-agent-runtime:<version>")
    // only if capturing HTTP traffic — match the app's client:
    implementation("com.kitakkun.jetwhale:jetwhale-network-inspector-agent-ktor:<version>")
    implementation("com.kitakkun.jetwhale:jetwhale-network-inspector-agent-okhttp:<version>")
}
```

A debug-only module needs the seam module itself: the app depending on both does not put one on
the other's classpath. When the wiring instead lives in the app module's `src/debug`, the seam in
`src/main` is already visible and that line goes away.

Take `<version>` from the [releases page](https://github.com/kitakkun/JetWhale/releases) — do not
guess it, and do not copy a version out of an older document.

**Which configuration** is the whole point:

| Project shape | How the dependency stays out of release |
|---|---|
| Android app or module | `debugImplementation`, on the module that will hold the JetWhale wiring |
| KMP, no Android variants | No variant concept exists. Prefer two thin entry-point modules (`:app-debug` / `:app-release`). A Gradle property gate (`if (providers.gradleProperty("jetwhale.enabled").orNull.toBoolean())`) is lighter, but it switches the whole invocation, release binaries included, so it must be passed on debug runs only and never set in a `gradle.properties` file |
| A dedicated debug-only module | The module itself is added with `debugImplementation`; inside it, plain `implementation` is correct |

If the answer is "the shared module everything depends on, with plain `implementation`", stop.
That ships JetWhale. Go back to §2.

## 4. Wire it — follow the reference for the project's DI framework

Each reference gives the seam, the two implementations, and the framework's mechanism for making
the debug one win. They share a shape: an app-owned interface, a no-op bound by default, and a
JetWhale-backed implementation that displaces it on the debug classpath only.

| Detected | Read | The edge that framework has |
|---|---|---|
| Metro (`dev.zacsweers.metro`) | `references/metro.md` | Empty multibindings need `@Multibinds(allowEmpty = true)` |
| kotlin-inject-anvil | `references/anvil.md` | **No empty multibindings at all** — use `replaces` for every seam |
| Square Anvil (maintenance mode) | `references/anvil.md` | **K1 only, so the project is held at Kotlin 2.2, below JetWhale's 2.3 floor** — it has to migrate (Metro, or a KSP-based Anvil fork) first |
| Hilt | `references/dagger-hilt.md` | `@InstallIn` is discovered, so the debug side is the only side |
| Plain Dagger | `references/dagger-hilt.md` | Modules are listed, so a same-name module per variant is unavoidable |
| Koin | `references/koin.md` | Runtime resolution — the compiler catches nothing |
| None | `references/no-di.md` | Same-signature factory per variant; KMP has no variants |

Every DI shape was verified by building and running a project, not reasoned about. See
[Verified against](#verified-against) for versions and evidence.

Whichever you follow, three JetWhale-side facts hold:

- **`startJetWhale { }` is called once**, as early as the app can — `Application.onCreate()`, the
  first line of `main()`, or the SwiftUI `App` init. It returns a session handle that a
  connect-once app can ignore.
- **The endpoint must be reachable from where the app runs.** The references declare
  `ws("localhost", 5080)`, which reaches the host from emulators, simulators, the desktop app, the
  browser and ADB-forwarded Android devices. A physical iPhone, or any device on Wi-Fi without
  `adb reverse`, cannot reach `localhost`; see [Physical devices](#physical-devices).
- **One `JetWhaleNetworkAgentPlugin` instance serves two call sites** — installed into the HTTP
  client, and registered in `plugins { register(...) }`. Two instances is the classic mistake: the
  app connects, the host lists the session, and no traffic ever appears. Give it a singleton
  binding and inject it in both places.

### Physical devices

When the app will run on a physical iPhone, or on a device without `adb reverse`, add a wss
candidate after `localhost` and say what to trust, as the demo app does:

```kotlin
connection {
    endpoints {
        ws("localhost", 5080)
        buildMachineWss(5443)
        discoverWss { allowHostName("<the host machine's name>") }
    }
    ssl { trustServerCertificate() }
}
```

- **`buildMachineWss(port)`** dials the address of the machine that compiled the app. It needs the
  `com.kitakkun.jetwhale.agent` Gradle plugin applied to the module that declares it.
- **`discoverWss { }`** finds the host over mDNS. Its block must allow a host (`allowHostName`), an
  address (`allowAddress`) or `allowAll()`; an empty block accepts nothing.
- **`ssl { trustServerCertificate() }`** is what lets either connect. Both dial the host's wss port
  (5443 by default), whose certificate comes from a CA the host issued itself, and no platform trust
  store holds that CA. With this, the agent fetches the CA from the host's `/jetwhale/ca` before the
  handshake and pins the connection to it, trusting it on first use. On a LAN you do not trust,
  `ssl { trustCertificate(pem = "...") }` pins a CA exported from the host's SSL certificate settings
  instead. With neither, the handshake fails on trust.
- **iOS `Info.plist`** needs `NSLocalNetworkUsageDescription` for any connection over the LAN, the CA
  fetch included, and `NSBonjourServices` listing `_jetwhale._tcp` for `discoverWss`:

  ```xml
  <key>NSLocalNetworkUsageDescription</key>
  <string>JetWhale connects to the debugger host running on your local network.</string>
  <key>NSBonjourServices</key>
  <array>
      <string>_jetwhale._tcp</string>
  </array>
  ```

[Getting Started](https://kitakkun.github.io/JetWhale/guide/getting-started) covers each in full.

## 5. Verify — the classpath, not the wiring

The wiring compiling proves nothing about release builds. Check the artifact:

```bash
# Android: nothing at all should come back from either
./gradlew :app:dependencies --configuration releaseCompileClasspath | grep -i jetwhale
./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep -i jetwhale

# Both variants must still compile — release is the one that catches a leaked reference
./gradlew assembleDebug assembleRelease      # or the KMP equivalents
```

Empty greps and a green release build together mean the seam holds: the compile classpath is what
proves no release call site can name JetWhale, since a `compileOnly` dependency would compile one
and still be absent at runtime. Then confirm the debug side actually works, because a perfectly
isolated integration that never connects is the other failure:

1. Launch the JetWhale host (default port **5080**).
2. Android only — the host's ADB auto port mapping forwards the port by default; if it is turned
   off, run `adb reverse tcp:5080 tcp:5080`.
3. Launch the debug build. It appears as a session in the host within a second or two.
4. If the Network Inspector is wired, make one request and watch it land.

Nothing in the host means the agent never connected: wrong port, no port forwarding, a physical
device left with only `localhost` or without `ssl { trustServerCertificate() }` (see
[Physical devices](#physical-devices)), or `startJetWhale` not reached. Session present but no
traffic means two agent instances — see §4.

## 6. Report what you did

State plainly:

- which seam you rode or created, and in which module
- the dependency configuration used, and the release-classpath grep result
- whether you saw the session connect, or only that it compiles — do not imply a live check you
  did not run
- anything you left unwired (e.g. OkHttp present but only Ktor wired) and why

## Verified against

Each pattern below was built as a four-module project (`:seam`, `:tooling`, `:app-debug` depending
on both, `:app-release` depending on `:seam` only) and **run**, so the recorded result is the
binding that actually resolved — not one inferred from the annotations. The projects used stand-in
classes in place of JetWhale's agents: they verify the DI shape and the classpath isolation, not a
JetWhale connection.

| Framework | Versions | Evidence |
|---|---|---|
| Metro | 1.3.2, Kotlin 2.4.10 | release `noop` + empty decorator set; debug real binding; `@SingleIn` holder identical across both injection sites |
| kotlin-inject-anvil | 0.1.7, kotlin-inject 0.9.0, KSP 2.3.10, Kotlin 2.3.10 | `replaces` resolves both ways; an empty `Set<T>` fails KSP outright |
| Square Anvil | 2.7.0, Dagger 2.60.1, Kotlin 2.2.20 | DI shape only: `replaces` resolves both ways once Dagger runs on kapt. Cannot host JetWhale, whose Kotlin floor is 2.3 |
| Dagger | 2.60.1 | `@BindsOptionalOf` → `Optional.empty()` in release, present in debug; `@Multibinds` allows empty with no parameter |
| Hilt + Android variants | 2.60.1, AGP 9.3.0, Kotlin 2.4.10 | generated component: `Optional.of(...)` in debug vs `Optional.empty()` in release, with no release-side module |
| Koin | 4.2.2 | `getAll` empty in release; `single` shares one instance |
| No DI + AGP variants | AGP 9.3.0, Kotlin 2.4.10 | debug APK dex carries the debug-only class, release APK carries zero occurrences |

Classpath isolation was checked on the built artifacts in the Android project: the debug-only
project appeared on `debugRuntimeClasspath`, was absent from `releaseRuntimeClasspath`, and its
classes were absent from the release APK's dex.

When a project's versions differ materially from these, re-check the sharp edge for that framework
before trusting the shape.

## Reference

- [Excluding from Release Builds](https://kitakkun.github.io/JetWhale/guide/excluding-from-release-builds)
  — the long-form version of §4, with the Metro examples in full
- [Getting Started](https://kitakkun.github.io/JetWhale/guide/getting-started) — the
  `startJetWhale { }` DSL, wss, per-platform startup locations
- [Network Inspector](https://kitakkun.github.io/JetWhale/guide/network-inspector) — Ktor and
  OkHttp adapters, mocking, redaction
