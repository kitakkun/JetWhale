import org.gradle.api.tasks.bundling.Jar
import org.gradle.process.CommandLineArgumentProvider
import util.JetWhalePluginExtension

/**
 * Convention for JetWhale host-plugin modules.
 *
 * Applying this convention gives plugin authors, for free:
 *
 * - `packagePlugin` — builds the distributable plugin fat-jar (bundles the module classes and its
 *   runtime dependencies). This is the artifact you drop into `~/.jetwhale/plugins/`. It replaces
 *   the hand-written `tasks.jar { from(configurations.runtimeClasspath.map(::zipTree)) ... }` that
 *   each plugin used to repeat.
 * - `installPlugin` — copies the packaged fat-jar into `~/.jetwhale/plugins/`.
 * - `runJetWhale` — the IntelliJ-`runIde`-equivalent: packages the plugin, places it in a private
 *   dev directory and launches the JetWhale host with `-Djetwhale.devPluginsDir=<dir>` so the host
 *   loads and hot-reloads the plugin under development. For live reload, run `runJetWhale` (which
 *   blocks while the host is open) in one terminal and `stageDevPlugin -t` in another — the host
 *   hot-reloads whenever the jar is re-staged. (Do not use `runJetWhale -t`: a blocking JavaExec
 *   never lets Gradle continuous mode start the next build.)
 */

val pluginExtension = extensions.create("jetwhalePlugin", JetWhalePluginExtension::class.java).apply {
    pluginArchiveName.convention(project.name)
    hostApplicationProject.convention(":jetwhale-host:app")
}

// ---------------------------------------------------------------------------
// packagePlugin: the distributable plugin fat-jar.
// ---------------------------------------------------------------------------

val packagePlugin = tasks.register<Jar>("packagePlugin") {
    group = "jetwhale"
    description = "Builds the distributable JetWhale plugin fat-jar (drop it into ~/.jetwhale/plugins/)."

    archiveBaseName.set(pluginExtension.pluginArchiveName)
    archiveClassifier.set("jetwhale-plugin")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // The module's own thin jar (compiled classes + bundled resources such as the plugin manifest).
    val thinJar = tasks.named<Jar>("jar")
    from(thinJar.map { zipTree(it.archiveFile) })

    // Bundle every runtime dependency, unpacked, into the same jar (the "fat" part).
    val runtimeClasspath = configurations.named("runtimeClasspath")
    dependsOn(runtimeClasspath)
    from({ runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) } })
}

// ---------------------------------------------------------------------------
// installPlugin: copy the packaged jar into ~/.jetwhale/plugins/.
// ---------------------------------------------------------------------------

val userPluginsDir = File(System.getProperty("user.home"), ".jetwhale/plugins")

tasks.register<Copy>("installPlugin") {
    group = "jetwhale"
    description = "Installs the packaged plugin jar into ~/.jetwhale/plugins/."

    from(packagePlugin)
    into(userPluginsDir)
}

// ---------------------------------------------------------------------------
// runJetWhale: launch the host with this plugin loaded from a dev directory.
// ---------------------------------------------------------------------------

// A private dev directory under the module's build folder. The host watches it and hot-reloads the
// plugin jar whenever it is re-staged (e.g. by running `stageDevPlugin -t` in a separate terminal).
val devPluginsDir = layout.buildDirectory.dir("jetwhale/devPlugins")

// Stage the freshly packaged plugin into the dev directory before launching the host.
val stageDevPlugin = tasks.register<Copy>("stageDevPlugin") {
    group = "jetwhale"
    description = "Copies the packaged plugin jar into the host dev plugins directory."

    from(packagePlugin)
    into(devPluginsDir)
}

// Resolve the host application (classes + runtime dependencies) from the configured host project so
// we can launch its main class directly with the dev system property set.
val jetwhaleHostRuntime = configurations.create("jetwhaleHostRuntime") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

// Wire the host project dependency lazily (no afterEvaluate, so it stays configuration-cache safe)
// while still honoring any override of `hostApplicationProject` set in the consumer's build script.
dependencies.addProvider(
    "jetwhaleHostRuntime",
    pluginExtension.hostApplicationProject.map { projectPath -> dependencies.project(projectPath) },
)

tasks.register<JavaExec>("runJetWhale") {
    group = "jetwhale"
    description = "Launches the JetWhale host with this plugin loaded for development (hot reload)."

    dependsOn(stageDevPlugin)

    classpath = jetwhaleHostRuntime
    mainClass.set("com.kitakkun.jetwhale.host.MainKt")

    // Point the host at the dev plugins directory; this enables dev-mode loading + hot reload.
    // Supplied lazily via a CommandLineArgumentProvider so the task stays configuration-cache safe.
    val devDirProvider = devPluginsDir.map { it.asFile.absolutePath }
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "-Djetwhale.devPluginsDir=${devDirProvider.get()}",
                // Allow the dev hot-reload to self-attach a JVM agent (byte-buddy-agent) for in-place
                // class redefinition; self-attach is disabled by default on JDK 9+.
                "-Djdk.attach.allowAttachSelf=true",
            )
        },
    )
}

// Make sure `packagePlugin` participates in the standard `assemble`/`build` lifecycle so authors get
// the distributable artifact without invoking the task by name.
tasks.named("assemble") {
    dependsOn(packagePlugin)
}
