# Example Plugin — Compose Hot Reload preview (PoC)

A proof-of-concept harness that renders a JetWhale plugin's UI under **JetBrains Compose Hot
Reload**, so plugin authors can edit UI code and see it reload **while Compose state is preserved**.

## Why this exists / how it differs from the runtime hot reload

The host app's runtime hot reload (`PluginHotReloadService`) reloads a plugin by loading its **jar
in a new classloader** and rebuilding the Compose scene — which **loses all plugin UI state**.

Compose Hot Reload instead **redefines the loaded classes in place** (JetBrains Runtime / DCEVM) and
recomposes, **preserving `remember` state**. But it only reloads classes on the **application
classpath** — it does not target dynamically loaded plugin jars. So this harness puts the plugin on
the classpath as a direct dependency (`:jetwhale-plugins:example:host`) and renders its
`ExamplePluginView` directly.

## Run it

Requires the **JetBrains Runtime (JBR, Java 21)**. Compose Hot Reload provisions a compatible JBR via
the foojay toolchain resolver (already configured in `settings.gradle.kts`). Compose Hot Reload ships
bundled with Compose Multiplatform 1.11.1, so no extra plugin is needed.

```shell
./gradlew :jetwhale-plugins:example:dev-preview:hotRun --auto
```

`--auto` enables Gradle continuous build, so saving a source file triggers a recompile + reload.

## What to verify

1. Click **"Send ping to debuggee"** a few times → the event log grows (this is the hoisted state).
2. Edit `ExamplePluginView` in `:jetwhale-plugins:example:host` — e.g. change a button label or add a
   `Text` — and save.
3. The window updates with the new code **while the event log entries remain** (state preserved).

## Known limits (what to expect)

- **Requires JBR** to run; on a stock JDK only method-body changes redefine.
- Method-body changes and (on JBR) adding/removing methods/fields reload fine; **changing a class's
  supertype or adding brand-new top-level classes/files** generally cannot be redefined.
- Restructuring a `@Composable` (changing the group structure) can reset the affected `remember`ed
  state.
- One plugin at a time (it is a direct classpath dependency here).

## Relationship to the production plugin model

Production loads plugins from external jars via isolated classloaders, which Compose Hot Reload does
not cover. The recommended end state is a **hybrid**: use this state-preserving reload during UI
development, and keep the classloader-based reload (state lost, but handles any change including new
jars/dependencies) as the fallback for the installed-jar workflow.

> Status: PoC. The setup compiles and the `hotRun`/`reload` tasks are available; the actual
> state-preserving reload behavior must be confirmed on a machine with the JetBrains Runtime (the GUI
> could not be run in CI).
