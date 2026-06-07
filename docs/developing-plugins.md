# Developing plugins

A host plugin is a fat-jar that JetWhale loads at runtime. Apply the `jetwhale-plugin` Gradle
convention to a plugin's host module to get the following tasks for free (see
`jetwhale-plugins/example/host` for a working example):

| Task            | What it does                                                                                   |
|-----------------|------------------------------------------------------------------------------------------------|
| `packagePlugin` | Builds the distributable plugin fat-jar (the artifact you drop into `~/.jetwhale/plugins/`).   |
| `installPlugin` | Copies the packaged fat-jar into `~/.jetwhale/plugins/`.                                        |
| `runJetWhale`   | Launches the JetWhale host with your plugin loaded from a private dev directory (`runIde`-like).|

## Live reload (dev mode)

`runJetWhale` starts the host with `-Djetwhale.devPluginsDir=<dir>` pointing at a dev directory
under the module's `build` folder. The host loads plugins from that directory **in addition to**
`~/.jetwhale/plugins/` and watches it: whenever the plugin jar is rebuilt, the host disposes the
plugin's running instances, drops its classloader, reloads the factory from a fresh classloader, and
refreshes the open plugin screen — no host restart needed.

`runJetWhale` is a long-running process (it blocks until you close the host), so do **not** add
`-t` to it — Gradle continuous mode only starts a new build once the current task graph finishes.
Instead, run the host in one terminal and continuous re-staging in another; the host watches the
dev directory and hot-reloads whenever the jar is re-staged:

```shell
# Terminal 1 — launch the host (stays running)
./gradlew :jetwhale-plugins:example:host:runJetWhale

# Terminal 2 — rebuild & re-stage the plugin jar on every source change
./gradlew :jetwhale-plugins:example:host:stageDevPlugin -t
```

When the `jetwhale.devPluginsDir` system property is absent (i.e. a normal production launch), dev
mode and hot reload are completely inert — behaviour is unchanged.

## Developing a plugin outside this repository

In-repo, `runJetWhale` builds the host from the local `:jetwhale-host:app` project. Plugin authors
working in their **own** repository don't have that project, so they download a released host and run
it, via `runJetWhaleFromRelease` (the JetWhale equivalent of IntelliJ's `runIde`):

1. Depend on the published SDK to compile the plugin (compile-time only — the host itself is not a
   library dependency):
   ```kotlin
   // host module
   compileOnly("com.kitakkun.jetwhale:jetwhale-host-sdk:<version>")
   // agent module, in the app being debugged
   implementation("com.kitakkun.jetwhale:jetwhale-agent-sdk:<version>")
   ```
2. Apply the convention and pin which released host to run against:
   ```kotlin
   plugins { id("jetwhale-plugin") }
   jetwhalePlugin { hostVersion.set("<version>") }
   ```
3. Run it (two terminals, same live-reload model as in-repo):
   ```shell
   ./gradlew :myPlugin:runJetWhaleFromRelease   # downloads the host uber jar for your OS + launches it
   ./gradlew :myPlugin:stageDevPlugin -t        # rebuild & re-stage on change
   ```

`runJetWhaleFromRelease` downloads the runnable host uber jar for `hostVersion` and the current
OS/architecture from the GitHub release (cached under `~/.jetwhale/dev-host/`), stages your plugin,
and launches it with hot reload — no manual install of JetWhale needed. State-preserving in-place
reload of method-body changes works on a stock JDK; structural changes fall back to a full reload
(the JetBrains Runtime is needed to redefine those in place).
