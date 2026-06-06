# Example Plugin — Compose Hot Reload preview (PoC)

A proof-of-concept that renders a **real** JetWhale plugin under JetBrains **Compose Hot Reload**, so
plugin authors can edit UI code and see it reload **while the plugin's Compose state is preserved**.

## What it actually exercises

Unlike a bare composable preview, the harness:

1. **discovers the plugin from the classpath via `ServiceLoader<JetWhaleHostPluginFactory>`** — the
   same `@AutoService` mechanism the host uses (so it works for any plugin on the classpath, not just
   the example), and
2. renders it through the SDK's `ContentRaw` entry point — the **same path the host uses**.

The plugin's state (its event-log `SnapshotStateList`) lives inside the plugin instance held by
`main()`. So the test is meaningful: does the **plugin instance's own state** survive a hot reload of
its UI code?

## Run it

Requires the **JetBrains Runtime (JBR, Java 21)**; Compose Hot Reload provisions a compatible JBR via
the foojay resolver (already configured in `settings.gradle.kts`). Compose Hot Reload ships bundled
with Compose Multiplatform 1.11.1, so no extra plugin is needed.

```shell
./gradlew :jetwhale-plugins:example:dev-preview:hotRun --auto
```

Then:
1. Click **"Send ping to debuggee"** a few times → entries are added to the plugin's event log.
2. Edit `ExamplePluginView` in `:jetwhale-plugins:example:host` (e.g. change a label) and save.
3. The UI updates with the new code **while the event log is preserved**.

## The important caveat: this is the *compile-dependency* path, not the jar path

This works only because the plugin is a **compile-time classpath dependency**, so Compose Hot Reload
recompiles and redefines it like ordinary app code.

Production is different: the host loads plugins from external jars (`~/.jetwhale/plugins/*.jar`) via
**isolated child classloaders** at runtime. Compose Hot Reload **cannot reach those**, because:

- its **continuous build recompiles the modules in the Gradle build**, not a plugin that is only
  present as a prebuilt jar at runtime; and
- its class redefinition targets classes on the **application (hot) classpath**, not classes loaded
  by an unrelated child classloader from an external jar.

So Compose Hot Reload is **not** a drop-in for the installed-jar reload.

## Recommended end state: hybrid

- **Plugin UI development** → develop the plugin as a compile dependency under Compose Hot Reload
  (this harness), getting state-preserving reloads.
- **Installed-jar workflow** → keep the classloader-based reload (loses state, but handles any change
  including new jars/dependencies) as the fallback.

A fully state-preserving reload of the *installed-jar* path would require approach B: a JVM agent
calling `Instrumentation.redefineClasses` on the plugin's loaded classes plus Compose's internal
reload hook — a separate, deeper spike.

## Toward an external "dev-host" (for plugin authors outside this repo)

This module proves the mechanism locally with project dependencies. The external-developer version
would be a Gradle `hotdev` task that resolves a **published dev-host launcher** plus the developer's
plugin module (as a source/compile dependency) and runs it under Compose Hot Reload on JBR.

Publishing notes:

- The host runs as a **binary dependency** — only the plugin needs to be a source/compile dependency
  to be hot-reloadable. So we publish the host to make it *runnable*, not reloadable.
- Prefer a **thin `dev-host` aggregator library** published as a normal jar (`api`-depending on the
  required host modules). The consumer adds **one** dependency and Gradle resolves the rest as normal
  jars, so the Compose/Kotlin runtime stays **shared** with the plugin module.
- Avoid a **shaded fat jar**: relocating Compose/Kotlin/coroutines breaks CHR (the plugin's Compose
  classes must match the host's), and a non-relocated bundle risks duplicate-class conflicts with the
  plugin's own transitive dependencies.
- `:jetwhale-host:app` is a Compose Desktop *application* (resources, packaging, DI graph), not a
  library — a dedicated minimal `dev-host` launcher module is the thing to publish, not `app`.

## Limits to expect

- **Requires JBR** to run; on a stock JDK only method-body changes redefine.
- Adding/removing methods/fields works on JBR; **changing a supertype or adding new top-level
  classes/files** generally cannot be redefined.
- Restructuring a `@Composable` (changing its group structure) can reset the affected state.

> Status: PoC. Compiles and the `hotRun` / `reload` tasks are available; the actual
> state-preserving reload must be confirmed on a machine with the JetBrains Runtime (no GUI in CI).
