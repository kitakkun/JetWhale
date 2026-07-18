import org.gradle.process.CommandLineArgumentProvider

/**
 * In-repo-only companion to the published `com.kitakkun.jetwhale.host` plugin.
 *
 * Adds `runJetWhaleLocal`, which launches the locally built host project (`:jetwhale-host:app`) with
 * this plugin staged for hot reload. It lives here, and is NOT published, because it depends on the
 * JetWhale repository's own host project — which external plugin authors don't have. They use
 * `runJetWhale` from the published plugin instead.
 *
 * Apply this alongside `com.kitakkun.jetwhale.host`: it reuses that plugin's `stageDevPlugin` task and its
 * dev plugins directory.
 */

// Must match the dev directory used by the `com.kitakkun.jetwhale.host` plugin's `stageDevPlugin` task.
val devPluginsDir = layout.buildDirectory.dir("jetwhale/devPlugins")

// Resolve the host application (classes + runtime dependencies) so we can launch its main class
// directly with the dev system property set.
val jetwhaleHostRuntime = configurations.create("jetwhaleHostRuntime") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
dependencies.add("jetwhaleHostRuntime", dependencies.project(":jetwhale-host:app"))

tasks.register<JavaExec>("runJetWhaleLocal") {
    group = "jetwhale"
    description = "Launches the local JetWhale host project with this plugin loaded for development (hot reload)."

    // `stageDevPlugin` is contributed by the `com.kitakkun.jetwhale.host` plugin applied to the same module.
    // Referenced by name (not tasks.named) so plugin application order doesn't matter.
    dependsOn("stageDevPlugin")

    classpath = jetwhaleHostRuntime
    mainClass.set("com.kitakkun.jetwhale.host.MainKt")

    // Point the host at the dev plugins directory; this enables dev-mode loading + hot reload.
    // Supplied lazily via a CommandLineArgumentProvider so the task stays configuration-cache safe.
    val devDirProvider = devPluginsDir.map { it.asFile.absolutePath }
    // Isolated, disposable app-data root for this plugin project so the host does not run against the
    // developer's real `~/.jetwhale`. Lives under `build/`, so it survives re-launches but `clean` wipes it.
    val sandboxDirProvider = layout.buildDirectory.dir("jetwhale-sandbox").map { it.asFile.absolutePath }
    val osName = providers.systemProperty("os.name")
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            buildList {
                add("-Djetwhale.devPluginsDir=${devDirProvider.get()}")
                add("-Djetwhale.appDataDir=${sandboxDirProvider.get()}")
                // Allow the dev hot-reload to self-attach a JVM agent (byte-buddy-agent) for in-place
                // class redefinition; self-attach is disabled by default on JDK 9+.
                add("-Djdk.attach.allowAttachSelf=true")
                // The macOS Dock name (hover text) comes from the bundle name, which for a bare JVM
                // can only be set via -Xdock:name at launch — it is not settable at runtime.
                if (osName.getOrElse("").contains("mac", ignoreCase = true)) add("-Xdock:name=JetWhale")
            }
        },
    )
}
