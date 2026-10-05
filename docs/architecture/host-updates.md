# Host updates — design

The desktop host ships as a jpackage installer (`.dmg`, `.msi`, `.deb`), and it has no working way
to update itself. This design makes the installer a launcher that is installed once, and moves
updates to the host jar. The host finds a newer release, downloads its jar and verifies it. The
launcher runs the newest good jar in its own process, and goes back to the previous one when a new
one does not start.

These points are decided: the launcher with a bundled runtime and a downloadable host jar, the
update check and download in the host, the removal of Conveyor, and no Apple Developer ID. Two
questions stay open: signing and channels. They are at the end, each with a recommendation.

## Where things stand

**Release assets.** A tag runs *Distribute Desktop Application*. It builds on three runners and
attaches the results to a draft release, which is published by hand:

| Platform | Installer | Host jar |
|---|---|---|
| macOS, Apple Silicon | `jetwhale-debugger-<version>-macos-arm64.dmg` | `jetwhale-host-<version>-macos-arm64.jar` |
| Linux x64 | `jetwhale-debugger-<version>-linux-x64.deb` | `jetwhale-host-<version>-linux-x64.jar` |
| Windows x64 | `jetwhale-debugger-<version>-windows-x64.msi` | `jetwhale-host-<version>-windows-x64.jar` |

The jars are `packageUberJarForCurrentOS` uber jars of about 120 MB, each with `Main-Class` set and
its own platform's skiko build. No checksum file is published; the API reports a `sha256:` digest
for each asset, which GitHub computes on upload. There is no Intel macOS build. Every release is a
prerelease, so `releases/latest` does not exist and the API answers 404. *Publish Snapshot* builds
the three jars again with `-PjetwhaleSnapshot` and overwrites them on a `<version>-SNAPSHOT`
prerelease.

**Packaging.** `:jetwhale-host:app` is packaged by the Compose plugin's `nativeDistributions`.
- The runtime image is jlinked from the JDK that Gradle runs on, which is Corretto 21 in CI through
  `setup-java`. It holds Compose's default modules plus `jdk.unsupported`, `java.naming`, `java.sql`
  and `java.instrument`. On macOS it is 79 MB.
- It has no `bin/java`: Compose's jlink step passes `--strip-native-commands` and has no public
  switch for it.
- The packaged app runs with `-Dcompose.application.configure.swing.globals=true`,
  `-Dapple.awt.application.appearance=system` and `-Dskiko.library.path=$APPDIR`, plus `-Xdock:name`
  on macOS. There is no `--add-opens`.
- `packageVersion` is the base version, because a DMG takes only `MAJOR[.MINOR][.PATCH]` and an MSI
  only `MAJOR.MINOR.BUILD` (at most 255.255.65535). Every installer so far is therefore `1.0.0`.
  [#389](https://github.com/kitakkun/JetWhale/pull/389) (open) gives the `.deb` versions like
  `1.0.0~alpha13`.

**macOS signing.** The 1.0.0-alpha12 app bundle and every library in it are signed ad hoc
(`flags=0x2(adhoc)`, no Team ID). The bundle has no hardened runtime and no entitlements. Compose
signs its app image ad hoc with the hardened runtime and its default entitlements,
`disable-library-validation` among them, but jpackage builds the DMG from that image and signs it
again ad hoc, which drops both. The bundle is not notarized, so `spctl` rejects it. A copy
downloaded with a browser is quarantined, and its first launch is blocked until the user allows it.
Since macOS 15, Control-click → Open no longer allows it. The user has to go to System Settings →
Privacy & Security → Open Anyway, which the getting-started guide does not mention.

Without the hardened runtime, library validation is off, so the JVM loads native libraries that
someone else signed. This was checked on the alpha12 bundle's own runtime, started through its own
launcher, with the alpha12 macOS jar on the class path and no `skiko.library.path`:
- Skiko extracted its dylib, which carries JetBrains' Developer ID signature, into its data
  directory and loaded it.
- `ProcessBuilder` started a child process.

The same bundle, re-signed ad hoc with the hardened runtime, got the JIT entitlements but not
`disable-library-validation`. It refused to load even its runtime's own `libjli.dylib`: *mapping
process and mapped file (non-platform) have different Team IDs*.

**Update check.** `UpdateCheckService` reads Conveyor's `metadata.properties` from
`releases/latest/download`, which fails for two reasons:
- Nothing has built that file since [#160](https://github.com/kitakkun/JetWhale/pull/160) moved the
  release workflow to jpackage.
- There is no latest release.

Its numeric mapping (`1.0.0-alpha08` → `1.0.0.8`) exists only for Conveyor.

**Gradle plugin.** `downloadJetWhaleHost` builds the asset URL from `jetwhalePlugin.hostVersion`
and the machine's `os-arch`. It caches the jar in `~/.jetwhale/dev-host/<version>/` and checks a
snapshot again by ETag. It checks no hash.

**App data.** `AppDataDirectoryProvider` roots everything at `~/.jetwhale`: `plugins/`,
`dataStorePreferences/`, `plugin-data/`, `ssl/` and `trusted-plugins.json`. The Gradle tasks
override the root with `jetwhale.appDataDir` for their sandboxes.

**IDE plugin.** `idea-plugin` bundles the host's classes and runs them inside the IDE through
`idea-host`. It is an IDE plugin (not published yet) and gets its updates the way IDE plugins do.
It uses neither the installer nor the launcher. The only change it sees is that the shared Updates
UI stays hidden in the IDE.

## Goals

- **An update takes one click and one restart.** No reinstall, no admin rights, no OS installer.
- **No update is applied behind the user's back.** Some users keep an older host to match an older
  agent SDK. The host offers an update, and the user downloads it and restarts.
- **A bad update does not lock the user out.** A version that fails to start is set aside, and the
  previous one runs.
- **Only what a release published runs.** Every jar is checked against its release's metadata
  before it is installed and before every start. Only signed metadata proves that the release job
  built the jar; with SHA-256 alone, the check catches corruption and truncated downloads (see
  *Signing the release metadata*). A process that can write to `~/.jetwhale` is out of scope (see
  *Verification and security*).
- **No Apple Developer ID or Windows code-signing certificate is needed.**

## Non-goals

- **Updating the OS package.** The launcher, its runtime and its installer change only through a
  reinstall. No installer runs silently.
- **Delta updates.** Each version is a whole jar.
- **Background downloads.** A download starts on the user's click.
- **Plugin updates.** Plugins keep their own install and trust flow.
- **Picking an older installed version from the UI.** An older release still runs with `java -jar`.

## Shape

```
GitHub Releases: jetwhale-host-<v>.json (+ .sig), jetwhale-host-<v>-<os-arch>.jar
      │ releases API, then download and verify
      ▼
host ── HostUpdateService ──▶ ~/.jetwhale/host/<v>/
      ▲                                         │
      │ loads the newest good jar into          │ verifies before each start
      │ its own JVM and calls its main          ▼
launcher (the OS package: runtime, launcher main, bundled host jar)
```

The launcher and the host are one process. The launcher chooses and verifies a version, then runs it
in its own JVM, and the process stays the app until the host ends.

## Release metadata

Each release carries `jetwhale-host-<version>.json`, written by the release job. If signing is
adopted, `jetwhale-host-<version>.json.sig` comes with it.

```json
{
  "format": 1,
  "version": "1.0.0-alpha14",
  "mainClass": "com.kitakkun.jetwhale.host.MainKt",
  "launcherContract": 1,
  "runtime": {
    "javaFeatureVersion": 21,
    "modules": ["java.base", "java.desktop", "java.instrument", "java.logging", "java.naming",
                "java.sql", "jdk.crypto.ec", "jdk.unsupported"]
  },
  "jvmArgs": ["-Dcompose.application.configure.swing.globals=true"],
  "platforms": {
    "macos-arm64": {
      "url": "https://github.com/kitakkun/JetWhale/releases/download/1.0.0-alpha14/jetwhale-host-1.0.0-alpha14-macos-arm64.jar",
      "size": 125156159,
      "sha256": "…",
      "jvmArgs": ["-Dapple.awt.application.appearance=system"]
    },
    "linux-x64": { "url": "…", "size": 119133124, "sha256": "…", "jvmArgs": [] },
    "windows-x64": { "url": "…", "size": 117418030, "sha256": "…", "jvmArgs": [] }
  }
}
```

- `version` equals the tag.
- `launcherContract` is the launcher contract the host relies on: the system properties it reads,
  the JVM argument forms, and the restart protocol. A launcher with a lower contract refuses the
  version.
- `runtime` gives the lowest Java feature version and the modules the host needs: the modules the
  build declares for its runtime image, Compose's defaults included.
- `jvmArgs` holds the common arguments; each platform entry adds its own. Only the forms the
  contract names are allowed: `-D…`, `--add-opens`, `--add-exports`, `--enable-native-access` and
  `-Xmx`. Agents, `-XX:OnError`-style hooks, argument files and class-path options are refused, so
  what a version can ask of the launcher stays explicit. The launcher sets a `-D…` argument as a
  system property; any other form has to be among the arguments its JVM started with (see
  *Starting the host*). A host on JDK 24 or later will want `--enable-native-access=ALL-UNNAMED`
  for skiko and JNA, or it warns at every start, so the package that runs it has to start its JVM
  with it, and this field is where the host says it needs it.
- `platforms` uses the same `os-arch` keys as the Gradle plugin. Each `url` is exactly the asset
  `downloadJetWhaleHost` fetches, so the Gradle plugin and the update service read the same asset
  names, and the names do not change.
- Readers ignore unknown fields, treat a `format` below 1 as malformed, and refuse one higher than
  they know.

One task in the release job writes the whole file from the jars it collected: their `url`, `size`
and `sha256`, and from the build the version, main class, Java version, modules and the JVM
arguments of every platform. The build keeps those values in one place, which the packaged app's
arguments and both kinds of metadata (the release's and the bundled one below) are written from, so
an argument added there reaches every host that runs. The release job also attaches `SHA256SUMS`
for every asset, for people who download installers by hand.

## Version order

A tag is `MAJOR.MINOR.PATCH`, optionally followed by `-alphaN`, `-betaN` or `-rcN`, where N is
1 to 199; the Windows version scheme below relies on that bound. Versions are compared on the three
numbers, then by stage (alpha, then beta, then rc, then no stage), then on N as a number:

`1.0.0-alpha9` < `1.0.0-alpha10` < `1.0.0-beta1` < `1.0.0-rc1` < `1.0.0` < `1.0.1-alpha1`

`alpha09` and `alpha9` are the same version, since the early tags are zero-padded. A tag that ends
in `-SNAPSHOT`, or that does not parse, is never a candidate. The release job refuses to build a
tag that does not parse, or that is not the version catalog's `jetwhale` version, which is the
version the host reports.

Strict SemVer compares `alpha10` and `alpha9` as text and gets them backwards. A comparison of
dotted numbers drops the suffix altogether. The order is therefore defined here and implemented
once, in a module shared by the host, the launcher and the release job.

## Launcher

### What the package holds

- **The runtime.** Corretto 21 as today, with no `bin/java`: the host runs in the launcher's JVM.
  It adds `java.management`, which the launcher reads its JVM's arguments with.
- **Room for later hosts.** The runtime cannot change until a reinstall, so it carries modules a
  later host may need: `java.net.http`, `jdk.management`, `jdk.zipfs`, `jdk.accessibility`,
  `jdk.net`, `jdk.crypto.cryptoki`, `jdk.charsets`, `java.scripting`, `java.security.jgss` and
  `jdk.httpserver`. It also carries `jdk.attach`, which ByteBuddy's self-attach uses through the
  system class loader; host classes cannot see it (see *Starting the host*). With Corretto 21 on
  macOS these modules take the image from 79 MB to 87 MB. All of `java.se` would make it 107 MB.
- **The launcher's main.** A small Kotlin module whose only dependency is the shared metadata
  module.
- **The host jar of the same release, with its metadata.** The jar is the file attached to the
  release, built in the same job, and the package holds it as `jetwhale-host.bundled`, so it stays
  off the launcher's class path. Its `release.json` is written by the same tool as the release's
  metadata, from the same build values, with this platform's entry only, including the jar's
  `url`, `size` and `sha256`. One model therefore reads both.

### Choosing a version

On each start, the launcher first settles a start an earlier launch left unjudged (see *Startup
window and rollback*). It then goes through the bundled version and the directories under
`~/.jetwhale/host/`, newest first:

1. It skips a version that is set aside, or that it sets aside now after two failed starts in a
   row, and a version it refuses (see *What the launcher refuses*).
2. It verifies the rest: the metadata's signature, if signing is adopted, then the jar's size and
   SHA-256 against the metadata. It deletes and logs a version that fails.
3. It runs the first version that passes.

The bundled version is part of the package the user installed and is not verified again, so the
signing key is needed only in the release job. The bundled version is also the floor: it is never
deleted or set aside, and its own launcher always runs it.

```
~/.jetwhale/host/
  1.0.0-alpha15/         jetwhale-host-1.0.0-alpha15-macos-arm64.jar, release.json, release.json.sig
  1.0.0-alpha14/         …
  staging/               downloads in progress; the launcher never reads it
  launcher-state.json    completed and set-aside versions, failed-start counts, the starting host
  launch.lock, instance.lock, instance.json
```

The directory keeps at most two versions, a download under way included: the one running and one
newer one.
- After a version completes a start, the launcher deletes every downloaded version older than it.
  Of the newer ones it keeps only the newest, which is set aside or finished downloading during
  the start, so the user can try it again or restart to it.
- When a download starts, the host deletes every downloaded version except the one running. The
  new version supersedes a set-aside one, or one waiting for a restart, so a set-aside version
  stays until the user downloads a newer one.
- On Windows a running host keeps its jar open, so a version still in use is deleted at a later
  start.

Only the launcher writes `launcher-state.json`, and only while it holds `launch.lock` (see *Single
instance, reopen and restart*). Each write replaces the file through a rename, so the host, which
only reads it, sees a whole file. Entries for versions no longer on disk are ignored. In the one
JVM, the judgment of a start, pruning and the shutdown hook share one holder of `launch.lock`,
because a JVM cannot take the same file lock twice.

### Starting the host

The launcher loads the chosen jar in a `URLClassLoader` whose parent is the platform class loader,
so its own Kotlin standard library and metadata classes stay out of the host's sight. It makes that
loader the thread's context class loader and calls the `main` of the metadata's `mainClass` on its
own main thread, with the arguments it received, without `--after` and `--retry`.

The launcher's process is then the app the OS knows: on macOS `com.kitakkun.jetwhale.host`, with
the bundle's name and icon, and on Windows `JetWhale Debugger.exe`. A child `java` process would be
another app to the OS. On macOS it is the runtime's `java`, with its own name and its own Local
Network permission, which keeps the host's mDNS advertising from working; on Windows it is
`javaw.exe`. Only the jar changes on an update, so the executable the OS approved, and the
permissions it granted the app, stay the same.

Before it calls the host, the launcher sets these system properties:
- each `-D…` argument of the metadata's `jvmArgs`, common then platform;
- `jetwhale.launcher.contract`, `jetwhale.launcher.executable` and `jetwhale.launcher.hostDir`;
- `jetwhale.launcher.setAsideVersion`, when this launch set a version aside, and clears it
  otherwise.

`jetwhale.appDataDir`, when it is set, is already this JVM's own property.

- **Other JVM arguments.** `--add-opens`, `--add-exports`, `--enable-native-access` and `-Xmx`
  cannot be applied to a JVM that runs. Such an argument has to be among the JVM's input arguments,
  which means the launcher package's own JVM arguments. The launcher refuses a version that asks
  for one its JVM did not start with (see *What the launcher refuses*).
- **Properties read at the JVM's start.** A `-D…` property that the JVM reads only as it starts has
  no effect when set this way, and it too belongs in the package's JVM arguments. A plugin's
  in-place hot reload needs one: `jdk.attach.allowAttachSelf`, which allows the self-attach it uses.
  The metadata format cannot tell such properties apart, and no current host asks for one. A host
  that needs one raises `launcherContract`, and the package that implements the new contract
  starts its JVM with the property, so an older launcher refuses that host instead of running it
  without the property.
- **`skiko.library.path`.** The launcher clears the `skiko.library.path` that Compose's packaging
  sets to the app directory. With the property set, skiko looks for its library in that directory
  only, which holds none, and fails. Without it, skiko extracts the library that matches the jar
  into `~/.skiko/` and loads it (checked above).
- **Modules on the application class loader.** Modules the runtime defines to the application class
  loader, `jdk.attach` and `jdk.internal.jvmstat` in this runtime, are not visible to host classes,
  so the launcher treats them as missing when a version declares one (see *What the launcher
  refuses*). ByteBuddy's self-attach loads the attach API through the system class loader and
  still works.
- **Crash logs.** A JVM fatal error log (hs_err) goes where the JVM puts it by default: the working
  directory, or the temporary directory when that is not writable. `-XX:ErrorFile` can only be set
  when the JVM starts, and the package cannot name the user's app data directory.
- **Output.** For a GUI start, the launcher points `System.out` and `System.err` at
  `logs/host-<version>.log` under the app data directory, truncated at each start, before it loads
  the host. Output that the JVM writes to the process's file descriptors itself, such as its crash
  messages, goes where the OS sends the app's. With `--headless` the host keeps the launcher's
  streams, so it writes to the terminal or the service manager that started it. The Windows
  launcher is a GUI program, as today's is, and has no console: a headless run in a Windows
  terminal uses `java -jar` on the host jar, or `runJetWhale`, as it does today.
- The launcher writes its own decisions to `logs/launcher.log`.

### Startup window and rollback

Before it calls the host, the launcher writes a `startingHost` record into `launcher-state.json`,
under `launch.lock`: the version, this process's ID, and the process's start time, which tells the
process from a later one that reuses its ID. The window runs 30 seconds from just before the host's
`main` is called, and the first of these ends it, recorded once under `launch.lock`:
- **Completed.** A timer finds the JVM still running at the end of the window. The version is
  recorded as having completed a start, its failed-start count is dropped, the record is cleared,
  and pruning runs.
- **Failed.** The host's `main` throws. The launcher writes the stack trace to the host's log and
  the failure to `launcher.log`, counts a failed start, clears the record, and exits with status 1.
  A throw after the window counts nothing, and the launcher still exits with status 1.
- **Neither.** The JVM shuts down inside the window and `main` did not throw: the user quit, the
  host ended the JVM with any exit status, the user restarted to update or chose *Try again*, or
  `main` returned and the host's threads ended. A shutdown hook clears the record.

A crash, a JVM fatal error or a kill leaves the record behind; a SIGTERM runs the shutdown hook and
is neither. The next launch, before anything else, finds that the record's process no longer runs,
or runs with another start time, and counts a failed start of that version. A record whose process
still runs belongs to a host in its window, and the instance check hands the launch to it.

The launcher stays the app until the host ends. With `--headless`, the JVM's exit status is the
host's own.

- **Setting aside.** The launcher counts the failed starts in a row of each version that has never
  completed a start. While choosing, it sets aside a downloaded version with two of them, and the
  same launch runs the next candidate with `jetwhale.launcher.setAsideVersion`. A failed start is
  not retried within its launch: the user's next launch starts the version again, and the one after
  its second failure falls back, whether that failure was a throw or a crash. The host it falls back
  to names the version that failed, links the log, and offers to try it again. *Try again* restarts
  as *Restart to update* does, adding `--retry <version>`; the new launcher clears the version's
  mark and its count, and chooses as usual. The bundled version is never set aside; it is started
  again.
- **Later crashes.** A version that has completed a start before is never set aside, and its
  failures are not counted. Its later crashes go to the host's crash recovery and its safe mode.
  Once the host has run on this machine, a plugin is the likelier cause.
- **#293's crash recovery.** Its run markers should record the host version. Otherwise the host the
  launcher falls back to counts the failed version's crashes as its own and starts in safe mode.
  Its 30-second grace should count from when the launcher calls the host's `main`, when this window
  starts, or accept that the process starts earlier by the launcher's own work: normally well under
  a second, longer after `--after` waited for the old host. It finds hs_err files where the JVM
  puts them by default (see *Starting the host*).
- **Nothing left.** If no candidate remains, the launcher shows its only UI: a dialog with the log
  location and the release page.
- **Unexpected errors.** An error the launcher did not expect, such as a file under `host/` that
  cannot be opened or written, goes to `logs/launcher.log` with its stack trace and to the same
  dialog (to stderr with `--headless`), and the launcher exits with status 1.

### Single instance, reopen and restart

The launcher's process is the host's, so macOS delivers a reopen of the app to the host itself, and
Finder activates the running app rather than starting a second one. Windows and Linux start a new
process on every launch, and macOS does with `open -n` or a second copy of the app. Two OS file
locks under `~/.jetwhale/host/` keep that to one host. The OS releases a file lock when its process
ends, so a crash leaves none behind.

- **One launcher at a time.** A launcher takes `launch.lock`, and waits while another launcher
  holds it. It keeps it until the host it runs has published `instance.json`, its start has been
  judged, or the JVM ends. Two launches at once therefore start one host.
- **Reopen.** The host takes `instance.lock` as it starts and holds it until its process ends, even
  if its reopen endpoint stops, so a later launch never starts a second host. Once its main window
  shows, or with `--headless` once its servers are bound, it writes a loopback endpoint, its process
  ID and a token to a temporary file, and renames that to `instance.json`, so a reader sees a whole
  record or none. It keeps the latest request to come forward until its window takes requests, and
  takes a request before it answers it. A launcher that finds `instance.lock` held asks that host to
  bring its window forward, then exits. If the record is missing or its endpoint does not answer,
  it reads it again for a few seconds, then logs the failure and exits. The launcher probes
  `instance.lock` with a lock it releases at once, before it calls the host, so the host's own lock
  in the same JVM never overlaps it. A host that cannot take `instance.lock` asks the running one to
  come forward in the same way and exits. On Windows and Linux this is new: today a second start
  runs a second host, which silently loses the race for the ports.
- **Restart to update.** The host starts the app again with `--after <pid>` and its own arguments,
  then exits normally.
  - On macOS it goes through LaunchServices, `open -n <app bundle> --args --after <pid> …`, so the
    new process is the app and not a child of the old one. LaunchServices starts the app with its
    own environment, so the host passes on the `JAVA_TOOL_OPTIONS` it started with, and no other
    variable. The host waits for `open`, and a non-zero exit, or no exit within ten seconds, is a
    failed restart.
  - Elsewhere it starts `jetwhale.launcher.executable` directly.

  The new launcher waits for that process to end before it takes `launch.lock`, because a host that
  restarts inside its window takes `launch.lock` on its way out, to judge its start. It then
  chooses as usual, and the new version is the newest one. A launch that slips in between finds the
  newest version or the host it started, so one host still results.
- **No restart.** If the user does not restart, the next normal start runs the new version.

### What the launcher refuses

The launcher does not start a version, and tries the next candidate, when:
- its `launcherContract` is higher than the launcher's;
- it needs a higher Java feature version than the runtime has, or a module its class loader cannot
  see: one the runtime lacks, or one the runtime defines to the application class loader;
- it has no entry for this platform, or asks for a JVM argument outside the contract's forms;
- it asks for a JVM argument other than `-D…` that the launcher's JVM did not start with;
- its signature or hash does not verify.

The host checks the same conditions before it offers a download. It runs in the launcher's JVM and
reads the contract from the system property, so a refusal at start is rare; it happens, for
instance, when versions are left in the cache from a newer install. When the newest release is one
this launcher cannot run, the host does not download it. A release with no jar for this platform is
reported as having no build for this computer. One that needs a higher contract, Java version or a
missing module, or a JVM argument outside the contract or missing from the JVM's arguments, needs a
new installer, and the host says so and links the release page. A reinstall is the only fix for the
runtime, its JVM arguments and the contract: the launcher never replaces itself.

## Host update service

A `HostUpdateService` (release lookup and selection, download, verification) and a repository for
the version directories replace `UpdateCheckService`, as
`agents/rules/jetwhale-host-architecture.md` asks. The UI reaches them through soil keys.

- **When.** On startup when the startup check is on, and when the user asks. The setting comes back
  with this service, after the Conveyor removal takes it out.
- **Lookup.** `GET https://api.github.com/repos/kitakkun/JetWhale/releases?per_page=30`,
  unauthenticated. Each check is one request, well under GitHub's limit of 60 an hour per address.
  The candidate is the highest release above the newest installed version that is not a draft,
  whose tag parses, and that carries the metadata asset. Snapshots, and releases from before this
  design, have no metadata and drop out.
- **Installed but not running.** A version that is installed and newer than the running one is
  offered as *Restart to update*, and a failed check leaves that offer in place. A set-aside one is
  offered as *Try again*, never as a new download.
- **Check.** The service fetches the metadata, and its signature when the release has one,
  verifies them, and checks the release against the running launcher (see above), including the
  arguments this JVM started with.
- **Banner.** At startup, the banner shows a newer release, and also one that needs a new
  installer, which links to the Updates section. A release with no build for this computer gets no
  banner.
- **Download.** It starts on the user's click, after the versions it supersedes are deleted (see
  *Choosing a version*), and shows progress and a cancel button. The jar goes into `host/staging/`.
  The download stops at the first byte past the metadata's `size` and is discarded as corrupted, so
  a wrong or endless answer cannot fill the disk. While the SHA-256 is checked, the section shows
  *Verifying*, still with Cancel, and a cancel then installs nothing. The finished directory is
  renamed into `host/<version>/`, so the launcher never sees half a version.
- **Offer.** The user can restart to update, or keep working; the next start runs the new version.
  When *Restart to update* or *Try again* cannot start the app again, because the launcher gave no
  path, the app was moved, or `open` failed or did not finish in time, the host keeps running and a
  banner says to quit and open the app again.
- **Failures.** A failed check and a failed download are reported apart, and neither blocks
  startup. The kinds:
  - GitHub's rate limit: a 403 or 429 with `x-ratelimit-remaining: 0`, or with `Retry-After`, which
    is how GitHub's secondary limit answers;
  - GitHub unreachable: the engine's `IOException`, or the `UnresolvedAddressException` that Ktor's
    CIO engine throws when a host name does not resolve, as it does offline;
  - an unexpected HTTP status;
  - metadata that does not verify, or whose `version` is not the release's tag;
  - a size or SHA-256 mismatch;
  - a download that could not be saved: an `IOException` from this computer's files, such as a full
    disk, is this kind, not unreachable.
- **Every end is defined.** Whatever ends a check or a download, the section leaves *Checking* or
  *Downloading*. A check that ends in an error the service does not know goes back to not checked,
  and a download that is cancelled or ends in such an error offers the release again. `staging/` is
  cleared in every case. One that cannot be cleared, such as a file another program holds on
  Windows, fails the download as not saved, and the next download tries again.

Without `jetwhale.launcher.contract`, the host was not started by the launcher, and the service
downloads nothing. In the IDE (`LocalEmbeddedInIde`) the Updates section and the banner are hidden,
because the plugin is updated as an IDE plugin. For `java -jar` and the Gradle tasks, the service
only links to the release page.

## Verification and security

- **The metadata decides what runs.** The jar must match the metadata's size and SHA-256 after the
  download and before every start. On an Apple Silicon Mac, hashing the 125 MB macOS jar takes
  0.1–0.2 s, which is small next to the host's own start.
- **GitHub's per-asset digest** is computed from whatever was uploaded. It guards the transfer, not
  against a replaced asset. The metadata's hash is the one that counts.
- **Immutable releases are off** (`immutable: false` on 1.0.0-alpha12). With GitHub's immutable
  releases on, assets and tags cannot change once a release is published, and the draft flow works
  with it, because CI attaches everything before the release is published. *Publish Snapshot*
  overwrites the assets of a published prerelease, though, and `runJetWhale` relies on that, so the
  snapshot flow has to change first.
- **The launcher's reach is limited.** It never writes into the installed package and needs no
  elevation. It runs only the bundled jar or a verified version directory under
  `~/.jetwhale/host/`, with paths resolved canonically, as `isManagedPluginJarPath` does for
  plugins.
- **The OS does not check downloads.** The host writes them, not a browser, so on macOS they carry
  no quarantine attribute and on Windows no Mark of the Web. Gatekeeper and SmartScreen never see
  them; the JVM reads a jar as data.
- **A local writer is out of scope.** `~/.jetwhale/host/` belongs to the user, like the rest of
  `~/.jetwhale`. A process that can write there runs as the user, and can already start code in
  their session without JetWhale, for instance from their login items or shell startup files. The
  check before each start catches a corrupted or truncated jar; it is not meant to stop such a
  process.

## Platform notes

### macOS without a Developer ID

- The package stays signed ad hoc without the hardened runtime, as today. After each reinstall, the
  first launch needs Open Anyway once.
- A downloaded jar's native libraries load as they do today (checked above). That holds only while
  the hardened runtime stays off, or comes with `disable-library-validation`.
- The host runs in the app's own process, `com.kitakkun.jetwhale.host`, with the bundle's name and
  icon, its Dock tile and its menu bar, so a pinned JetWhale Debugger tile is the running host's.
  No separate executable is signed or approved, and what macOS grants the app, such as Local
  Network access for the host's mDNS advertising, applies to every host version.
- This was checked with a Finder launch of a test build: the host ran as
  `com.kitakkun.jetwhale.host` with no child process, and its mDNS service resolved to the host's
  ports with no Local Network prompt and no `NoRouteToHostException`.
- Since macOS 14, an app that is not active may not be allowed to bring its window forward on its
  own. Phase 2 checks that the host still comes forward when a launcher asks it to.

### Windows

- **The MSI version matters only for reinstalls.** The MSI is installed once, so its version comes
  into play only when one MSI is installed over another: a launcher reinstall, or a user who
  prefers installers. jpackage derives the ProductCode from the vendor, name and version, so to
  Windows Installer two MSIs of the same version are the same product. The second one stops with
  *Another version of this product is already installed*. Every MSI so far is `1.0.0`, so today a
  Windows user has to uninstall before installing the next alpha.
- **Proposed scheme.** jpackage's template allows both upgrades and downgrades between different
  versions, so a distinct version per release is enough, and an ordered one costs nothing more.
  Compose's `msiPackageVersion` would be `MAJOR.MINOR.(PATCH × 1000 + S)`, where S is 100 + N for
  alphaN, 300 + N for betaN, 500 + N for rcN and 900 for a final release. Since N is at most 199
  (see *Version order*), the stages' ranges do not overlap, and the build stops on a larger N
  rather than produce a version another release has. `1.0.0-alpha14` becomes `1.0.114`, `1.0.0`
  becomes `1.0.900`, and `1.0.1-alpha1` becomes `1.0.1101`. This stays within MSI's limit of 65535
  up to patch 64. The first MSI on this scheme is the first one not at `1.0.0`, and it replaces an
  old install instead of failing.
- The MSI installs per machine into Program Files, so all mutable state lives in the user's
  `~/.jetwhale`.
- The host runs in `JetWhale Debugger.exe`'s process, so the taskbar shows the app, not a
  `javaw.exe`.
- SmartScreen warns about the unsigned MSI at install, as it does today.

### Linux

- The `.deb` keeps #389's version: the catalog version with `~` (`1.0.0~alpha13`), and no epoch.
  apt sees a new `.deb` only when the launcher is reinstalled. #389's order still makes that an
  upgrade, and #389's reasons against an epoch only get stronger.
- The package is installed under `/opt` and owned by root, so state lives in `~/.jetwhale`.

## Migration

Hosts installed from today's packages cannot hear about the launcher, because their update check
never succeeds. The release that introduces the launcher asks for one reinstall, in its notes, the
README and the getting-started guide:
- **macOS.** Replace the app in Applications from the new `.dmg`, then use Open Anyway once.
- **Windows.** Install the new `.msi`. With the version scheme above it replaces the old install;
  without it, the user has to uninstall first.
- **Linux.** Run `sudo apt install ./jetwhale-debugger-<version>-linux-x64.deb`. Coming from a
  `1.0.0`-versioned alpha, the user confirms the downgrade prompt once (#389).

A reinstall replaces the old app only if the package keeps today's identity: the name
`JetWhale Debugger` and the vendor, which give the MSI's UpgradeCode and the `.deb`'s name, and the
macOS bundle ID `com.kitakkun.jetwhale.host`. Today that ID comes from the main class's package, so
it has to be set explicitly once the main class is the launcher's.

Settings, plugins and the trust registry in `~/.jetwhale` stay untouched, and `~/.jetwhale/host/`
is new. `runJetWhale` and the IDE plugin are not affected.

## Removing Conveyor

Conveyor has been dead code since #160. Its removal is a separate PR and can land first. It removes:
- `conveyor.conf` and the Conveyor Gradle plugin;
- the numeric project version in `jetwhale-host/app/build.gradle.kts`, which exists for Conveyor
  and also names today's app jar (`app-1.0.0.12-….jar`);
- the four platform dependencies that only Conveyor resolves (`linuxAmd64`, `macAmd64`,
  `macAarch64`, `windowsAmd64`);
- `conveyor` and `conveyorControl` from the version catalog, and `conveyor-control` from
  `core:data`;
- the update check built on Conveyor: `UpdateCheckService` and its numeric mapping, the check and
  install mutation keys, the startup check and its setting (also exposed over MCP), the banner, and
  the Updates section;
- what the guides say about it: the Updates section in `docs/guide/host-settings.md`, and
  `checkForUpdatesOnStartup` in `docs/guide/mcp-server.md`.

Phase 3 brings the section and the setting back on the new service. The toolchain's Corretto pin
also mentions Conveyor in its comment. jpackage bundles the JDK that Gradle runs on, not the
toolchain, so the removal PR decides whether the pin still earns its place.

## Open decisions

### Signing the release metadata

**Recommendation: Ed25519, minisign-style.**
- **What is signed.** CI signs the metadata file with a detached Ed25519 signature. The jar hashes
  inside it extend the signature to the jars, so each release signs one small file.
- **The keys.** The private key is a CI secret in an environment that only tag builds can use, and
  only the release job reads it. The public keys are compiled into the launcher and the host. The
  signature names its key, so a second key, kept offline, can be embedded from the start. If the
  first key is lost, releases switch to the second without a reinstall. Revoking a leaked key is
  designed along with signing, if signing is adopted.
- **Verification.** The JDK verifies Ed25519 itself with `Signature.getInstance("Ed25519")`, so the
  launcher needs no crypto library. On JDK 21 the provider is in `jdk.crypto.ec`, which the runtime
  already includes.
- **Tooling.** By default, minisign signs a BLAKE2b prehash, which the JDK cannot compute. Signing
  the file itself keeps verification inside the JDK; minisign's legacy mode or
  `openssl pkeyutl -rawin` does that.

What it buys: a release that did not come out of the release job no longer runs. Whoever can get
the release job to run on a tag of their choice still gets a signed build, so the cover is:
- an asset replaced or edited on an existing release;
- a release made with a leaked workflow `GITHUB_TOKEN`, whose tag pushes start no workflow;
- a release made with a leaked personal token, but only when a tag ruleset stops that token from
  creating tags, or the signing environment requires a reviewer it cannot stand in for.

Immutable releases alone stop the first, not the other two.

**Alternative: SHA-256 only.** There is no key to guard, rotate or lose. But the hashes come from
the same release as the jars, so they catch corruption and truncated downloads, not someone who can
change the release. Anyone who can publish a release can then run their code on the machine of
every user who accepts the update.

### Channels

**Recommendation: all releases now, stages later.**
- **Now.** Every release is a prerelease, so there is nothing to choose between yet, and GitHub's
  prerelease flag tells nothing apart.
- **Later.** Once a final release exists, the channel follows from the version's stage, and the
  metadata needs no field for it. A setting with *Stable*, *Beta and stable* and *All* then
  defaults to the stage of the installed version, so a user on a stable release stays on stable.

## Testing

- **Shared module.**
  - The version order: the chain above, `alpha09` = `alpha9`, and no snapshot, unparseable tag or
    N outside 1–199 as a candidate.
  - Metadata parsing: unknown fields, a `format` of 0 or below and one higher than known, and
    deeply nested JSON.
  - Signature checks with a test key pair: valid, tampered, and wrong key.
  - The JVM argument forms.
- **Launcher.**
  - Unit tests run launches one after another as separate fake processes, with fake locks that a
    fake process releases when it ends, a fake process table and a test clock. They cover crash
    records, a reused process ID, a throw from `main`, a shutdown before and after publication, two
    failures setting a version aside and the fallback naming it, the bundled version never set
    aside, `--retry`, the restart waiting without `launch.lock`, two launches at once, and
    `launch.lock` held until publication and taken again to record a completed start.
- Process tests run the launcher's real `main` in a child JVM with a stub host jar written in Java,
    so it needs no Kotlin. They check that the host's class loader sees neither Kotlin nor the
    launcher, that the contract's properties and the metadata's `-D…` arguments are set and
    `skiko.library.path` is cleared, that output goes to the host's log, that a throw counts a
    failed start and exits with status 1, that `System.exit` inside the window is neither, and that
    the next launch counts a `Runtime.halt` as a failed start.
- **Host update service.** Ktor's `MockEngine`, as `UpdateCheckServiceTest` uses it today.
  - Release selection from recorded API responses: drafts, snapshots, missing metadata, a missing
    platform, and installed or set-aside versions.
  - Failures: both rate limits, offline from the releases URL and from the jar URL, an unexpected
    status, metadata for another version than its tag, size and hash mismatches, a body past the
    pinned size, an unwritable host directory, an undeletable leftover in `staging/`, and an error
    the service does not know.
  - The flow: redirects to the asset host, an interrupted download, a cancel while verifying, a
    check or a second download while one runs, the rename out of staging, and a launcher that
    cannot be started.
- **Release job.** Before attaching anything, the job checks the metadata it just wrote with the
  launcher's own verifier.
- **Packages, by hand on each OS,** built by a manual run of *Distribute Desktop Application* as a
  test build:
  - a fresh install;
  - an update from a local release source, signed with a test key if signing is adopted;
  - restart to update;
  - a rollback with a jar that fails on purpose;
  - a refusal for a version that needs a newer runtime;
  - reopening while the host runs;
  - on macOS, an install downloaded through a browser.

  A test build reads the release source from an environment variable, which the host inherits
  from the launcher, and embeds the test public key if signing is adopted. Production builds have
  neither.
- **Checked on macOS** with a test-build DMG, a scratch app data directory, a local release source,
  and every launch through `open -n`. Each host ran as `com.kitakkun.jetwhale.host` with no child
  process and logged no `NoRouteToHostException`.
  - The bundled version completed its start, and a second launch handed off to the running host.
  - An update downloaded, and its restart went through `open -n` with `JAVA_TOOL_OPTIONS` passed on.
    The new launcher waited for the old host without `launch.lock`, then ran the new version.
  - A version that halted the JVM and then threw from `main` was counted twice, the halt by the
    next launch. The third launch set it aside and ran the previous version with the set-aside
    banner.
  - Quitting inside the window counted nothing, also for a version that had never completed a start.
  - A jar changed after install was deleted at start, and the bundled version ran.

## Plan

1. **Release metadata and checksums in CI.**
   - The shared module: the metadata model, the version order and the verifier.
   - One place in the build for the host's runtime values: Java version, modules and JVM
     arguments.
   - In the release job: the tag check, one task that writes the whole metadata from the collected
     jars, `SHA256SUMS`, signing if that is decided, verification, and attaching.

   Nothing reads the metadata yet, so this phase can ship on its own. It gives users `SHA256SUMS`
   and runs the release path before anything depends on it.
2. **Launcher.**
   - The launcher module, and the packages built from it: the bundled uber jar and its metadata,
     the extra modules, and today's package identity.
   - The MSI version scheme. It can also go earlier on its own, because it already fixes installing
     over an earlier alpha.
   - On the host side: the launcher properties, the locks and activation, and the restart with
     `--after`, through `open -n` on macOS. In #293's crash recovery: the version in its run
     markers, a grace counted from the host's `main`, and hs_err files where the JVM puts them.
3. **Host update service and UI.** `HostUpdateService`, the version repository, the Updates section,
   the startup setting and the banner, and the set-aside, refusal and failure messages. All of it is
   hidden in the IDE.
4. **Conveyor removal.** As described above.

Phases 2 and 3 ship in the same release. A launcher whose host cannot download updates would force
its users to reinstall a second time to get phase 3. The merge order is 1 → 2 → 3, with the release
after 3; phase 4 can land at any point, first included.

## Risks

- **A rollback runs an older host on data a newer one wrote.** DataStore Preferences ignores
  unknown keys. The JSON stores (the trust registry, plugin data) have to decode with unknown keys
  ignored, and a file format change needs a version guard rather than a rewrite in place.
- **The runtime and its JVM arguments are fixed at install.** A host that needs a newer Java, a
  module the runtime lacks, or a JVM argument the package does not start with, needs a reinstall.
  The extra modules make that rare, and the check before download makes it clear.
- **GitHub API limits.** 60 unauthenticated requests an hour per address can run out behind a
  shared NAT. A failed check is shown and changes nothing.
- **Disk.** Up to three host jars of about 120 MB each: the bundled one, the running one, and one
  newer one that is set aside, waiting for a restart, or being downloaded.
