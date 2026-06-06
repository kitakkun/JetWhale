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
