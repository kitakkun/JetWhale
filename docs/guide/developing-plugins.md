# Developing plugins

A JetWhale host plugin is a fat-jar that the JetWhale host loads at runtime. You develop it in your
**own** repository: compile against the published SDK, and use the published `com.kitakkun.jetwhale.host`
Gradle plugin to package it and to run a real host with your plugin loaded — with **hot reload**, so
you can edit your plugin and see it update without restarting the host.

The `com.kitakkun.jetwhale.host` plugin gives your plugin's module these tasks:

| Task                     | What it does                                                                                          |
|--------------------------|------------------------------------------------------------------------------------------------------|
| `packagePlugin`          | Builds the distributable plugin fat-jar (the artifact you drop into `~/.jetwhale/plugins/`).          |
| `installPlugin`          | Copies the packaged fat-jar into `~/.jetwhale/plugins/`.                                              |
| `stageDevPlugin`         | Stages the packaged fat-jar into a private dev directory the host watches for hot reload.             |
| `runJetWhale`            | Downloads a released JetWhale host for your OS and launches it with your plugin loaded.|
| `runJetWhaleHot`         | Like `runJetWhale`, but runs the host on the JetBrains Runtime so structural changes hot-reload in place (see [Limitations](#limitations)), and auto re-stages your plugin in the background — the whole hot-reload loop in one command. |

## Set up

### 1. Repositories

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
```

### 2. Apply the plugin and pin a host version

A JetWhale plugin module is a Kotlin/JVM module with Compose UI. Apply `com.kitakkun.jetwhale.host`, set
`hostVersion` to the released host you want to run against, and depend on the SDK at compile time only
(the host provides it at runtime):

```kotlin
// the plugin's host module — build.gradle.kts
plugins {
    kotlin("jvm") version "<kotlinVersion>"
    id("org.jetbrains.kotlin.plugin.compose") version "<kotlinVersion>"
    id("com.kitakkun.jetwhale.host") version "<version>"
}

dependencies {
    // Provided by the host at runtime, so compileOnly — they must NOT be bundled into the plugin jar.
    compileOnly("com.kitakkun.jetwhale:jetwhale-host-sdk:<version>")
    compileOnly("org.jetbrains.compose.material3:material3:<composeMaterial3Version>")
}

jetwhalePlugin {
    hostVersion.set("<version>")
}
```

The agent SDK goes in the **app being debugged** (a normal runtime dependency, not in the plugin
module):

```kotlin
implementation("com.kitakkun.jetwhale:jetwhale-agent-sdk:<version>")
```

### 3. Write the plugin

Implement `JetWhaleHostPluginFactory` and declare it in a plugin manifest. The host loads each plugin
by instantiating the `factoryClass` named in the manifest. See `jetwhale-plugins/example/host` for a
complete, working example:

- `src/main/kotlin/.../MyPluginFactory.kt` — a `JetWhaleHostPluginFactory` returning your
  `JetWhaleRawHostPlugin` (or the typed `JetWhaleHostPlugin<Event, Method, MethodResult>`). It needs a
  public no-arg constructor so the host can instantiate it.
- `src/main/resources/META-INF/jetwhale/plugin-manifest.json` — one entry per plugin under `plugins`,
  each with `pluginId`, `pluginName`, `version`, and `factoryClass` (the fully-qualified name of the
  factory above):

  ```json
  {
    "plugins": [
      {
        "pluginId": "com.example.myplugin",
        "pluginName": "My Plugin",
        "version": "1.0.0",
        "factoryClass": "com.example.MyPluginFactory"
      }
    ]
  }
  ```

#### Multiple plugins in one module

A single module's jar can ship several plugins: add one entry per plugin to the `plugins` array, each
pointing at its own `factoryClass`. The plugins share the jar (and its classloader), and are loaded,
reloaded, and hot-redefined together.

## Hot reload (the live dev loop)

`runJetWhale` starts the host with `-Djetwhale.devPluginsDir=<dir>` pointing at a dev
directory under your module's `build` folder. The host loads plugins from that directory **in addition
to** `~/.jetwhale/plugins/` and watches it: whenever the plugin jar is re-staged, the host reloads it
and refreshes the open plugin screen — **no host restart needed**. For simple edits it redefines your
classes in place and keeps the plugin's state; for changes it can't apply that way it recreates the
plugin from a fresh classloader (see [Limitations](#limitations) below).

The simplest loop is a single command — `runJetWhaleHot` launches the host **and** keeps re-staging
your plugin for you:

```shell
# Launches the host and re-stages on every source change, all in one terminal.
./gradlew :myPlugin:runJetWhaleHot
```

It runs the host in the foreground and, in the background, a `stageDevPlugin -t` that re-packages and
re-stages the jar whenever you edit a source file; the host then hot-reloads it. The background
re-staging stops automatically when you stop the host (close it, or press Ctrl+C). `runJetWhaleHot`
also runs the host on the JetBrains Runtime so structural changes hot-reload in place — see
[Limitations](#limitations).

If you prefer a plain JDK (no JBR toolchain), use `runJetWhale` instead and drive the re-staging
yourself from a second terminal:

```shell
# Terminal 1 — download + launch the host (stays running)
./gradlew :myPlugin:runJetWhale

# Terminal 2 — rebuild & re-stage the plugin jar on every source change
./gradlew :myPlugin:stageDevPlugin -t
```

> Do **not** add `-t` to `runJetWhale`/`runJetWhaleHot`: they are long-running processes (they block
> until you close the host), and Gradle continuous mode only starts a new build once the current task
> graph finishes. `runJetWhaleHot` already runs the watcher for you; for `runJetWhale`, keep the host
> in one terminal and `stageDevPlugin -t` in another.

`runJetWhale` downloads the runnable host uber jar for `hostVersion` and the current
OS/architecture from the GitHub release (cached under `~/.jetwhale/dev-host/`) — no manual install of
JetWhale needed. Pass `-PjetwhaleHostJar=<path>` to launch a locally built host uber jar instead.

### Limitations

Hot reload always keeps you working without a host restart, but **how much of your plugin's in-memory
state survives** depends on the kind of change you made:

| Change you made                                                            | What happens                                              |
|----------------------------------------------------------------------------|-----------------------------------------------------------|
| Edit a **method body**                                                      | Redefined in place — the plugin instance and its state are **kept**. |
| **Structural** change: add/remove a method or field, change a signature or supertype | Can't be redefined in place → **full reload**; the plugin is recreated and its in-memory state **resets**. |
| Add a **new class/file**, or change **dependencies** (new jars)            | **Full reload** — the plugin's classloader is dropped and rebuilt from the new jar. |
| Change the **plugin manifest** (`pluginId`, icon, version)                 | Picked up on the next stage as a **full reload**.         |

Compose-specific: restructuring a `@Composable` (changing its group structure) can reset the state
held by that part of the UI even when the rest of the plugin is preserved.

On a **stock JDK**, only method-body edits are redefined in place; everything else falls back to a
full reload. To preserve state across **structural** changes too, launch with **`runJetWhaleHot`**,
which runs the host on the **JetBrains Runtime (JBR)** with enhanced class redefinition. JBR is
provisioned via Gradle toolchains — add the
[foojay resolver](https://github.com/gradle/foojay-toolchains) to your `settings.gradle.kts` so it
can be downloaded automatically:

```kotlin
plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }
```

So: a change that can't be redefined in place costs you the plugin's in-memory state — never a host
restart.

## Trying an unreleased (SNAPSHOT) build

Pre-release builds are published as `-SNAPSHOT`. To try one, add the Central snapshots repository to
**both** repository blocks in `settings.gradle.kts` and use a `-SNAPSHOT` version everywhere
(`id("com.kitakkun.jetwhale.host") version`, the SDK dependency, and `hostVersion`):

```kotlin
maven("https://central.sonatype.com/repository/maven-snapshots/")
```

## Developing inside this repository

In-repo plugin modules (e.g. `jetwhale-plugins/example/host`) don't download a host — they launch the
local `:jetwhale-host:app` project directly via `runJetWhaleLocal`, which is added by the internal,
non-published `jetwhale-host-launch` convention applied alongside `com.kitakkun.jetwhale.host`:

```shell
./gradlew :jetwhale-plugins:example:host:runJetWhaleLocal   # builds + launches the local host
./gradlew :jetwhale-plugins:example:host:stageDevPlugin -t
```

The hot-reload model is identical; only the source of the host differs (local project vs downloaded
release).

When the `jetwhale.devPluginsDir` system property is absent (i.e. a normal production launch), dev
mode and hot reload are completely inert — behaviour is unchanged.
