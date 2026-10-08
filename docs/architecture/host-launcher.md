# Host launcher — how a launch runs

The packaged app is a launcher. It picks a host version, checks it, and runs that version's jar
in its own JVM. To the OS, the running host *is* the app: on macOS it has the app's bundle ID and
Dock tile, and it keeps the permissions the app was granted. The design behind it, including how
the host downloads new versions, is in [host-updates.md](./host-updates.md).

```
JetWhale Debugger (.app / .exe / deb)
└─ jpackage launcher ─ JVM
   └─ LauncherMain.main          picks a version and records how its start goes
      └─ InProcessHost.run       loads that version's jar, calls its main on the same thread
         └─ host MainKt.main     runs until the app quits
```

## Files under `<app data>/host/`

| File | Written by | Holds |
|---|---|---|
| `<version>/` | the host's update service | a downloaded jar and its `release.json` |
| `launcher-state.json` | the launcher only | completed, set-aside and failed-start versions, and the started host process |
| `launch.lock` | launchers | lets one launcher choose and record at a time |
| `instance.lock` | the running host | held for the host's lifetime |
| `instance.json` | the running host | loopback port, pid and token for the `bring-to-front` request |

## A launch, step by step

1. **Wait for `--after <pid>`** if given, for at most 60 s. This is a host restarting into an
   update.
2. **Take `launch.lock`.**
3. **Judge the last start.** If `launcher-state.json` still records a start and its process is
   gone, that start crashed or was killed: count a failed start of that version.
4. **Clear `--retry <version>`.** Remove its set-aside entry and failed-start count.
5. **Hand off to a running host.** If `instance.lock` is held, send `bring-to-front <token>` to
   the port in `instance.json`, then exit.
6. **Choose a version.** Go through the downloaded versions newer than the bundled one, newest
   first, then the bundled one. Take the first that:
   - is not set aside (two failed starts in a row set a version aside here);
   - has readable metadata for this version;
   - this launcher can run: its contract, Java version, modules visible to the host, platform,
     and every non-`-D` JVM argument (see
     [What the launcher refuses](./host-updates.md#what-the-launcher-refuses));
   - has a jar matching its pinned size and SHA-256.

   Broken or mismatched versions are deleted. Versions this launcher can't run are skipped.
7. **Record the started host process** (`startedHostProcess`: version, pid, process start time).
   `launch.lock` stays held.
8. **Run the host:**
   - set the metadata's `-D` arguments and the launcher contract as system properties;
   - send output to `logs/host-<version>.log` (not with `--headless`);
   - load the jar in a `URLClassLoader` whose parent is the platform loader;
   - call its `main`.
9. **The host takes `instance.lock`.** Once its window shows (with `--headless`, once its servers
   are bound), it publishes `instance.json`. The launcher then lets `launch.lock` go.

## How a start is judged

The first of these to happen within the *startup time window*, the 30 seconds from step 8, is
recorded, under `launch.lock`:

| What happens | Judgment | Effect |
|---|---|---|
| Still running after 30 s | completed | Clears its failures. Deletes every downloaded version except this one and the newest newer one |
| The host's `main` throws | failed | Counts a failure, exits 1 |
| JVM shuts down without a throw (a quit, a restart) | neither | Clears `startedHostProcess` |
| Crash, hs_err or kill: nothing gets recorded | failed | Counted at the next launch (step 3) |

A version that has completed a start before is never counted as failing again, so it is never set
aside. A crash after the startup time window is recorded nowhere: the next launch starts the same
version again. The bundled version is never set aside. The reasons are in
[Startup time window and rollback](./host-updates.md#startup-time-window-and-rollback).

## Common paths

- **Double-click while running:** step 5 brings the window forward. On macOS, Finder usually
  brings the running app to the front without starting a second process.
- **Update:** the host downloads `<version>/` and offers **Restart to Update**. It then starts the
  launcher with `--after <its pid>` (on macOS through `open -n`) and quits. The new launcher picks
  the new version at step 6.
- **A new version that crashes:** after its first failed start, the next launch tries it again.
  After the second, the next launch sets it aside at step 6, and the previous version runs with a
  banner naming it.
- **Try Again:** the host restarts the launcher with `--retry <version>`.
