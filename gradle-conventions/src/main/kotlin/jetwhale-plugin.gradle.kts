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

// ---------------------------------------------------------------------------
// runJetWhaleFromRelease: download a released host and launch it with this plugin, for plugin
// authors developing OUTSIDE this repository (the `:jetwhale-host:app` project is not available to
// them). Set `jetwhalePlugin.hostVersion` to choose which released host to run against.
// ---------------------------------------------------------------------------

// "<os>-<arch>" of the current machine, matching the host uber-jar release asset names.
val currentOsArch: String = run {
    val osName = System.getProperty("os.name").lowercase()
    val os = when {
        osName.contains("mac") || osName.contains("darwin") -> "macos"
        osName.contains("win") -> "windows"
        else -> "linux"
    }
    val archName = System.getProperty("os.arch").lowercase()
    val arch = if (archName.contains("aarch64") || archName.contains("arm")) "arm64" else "x64"
    "$os-$arch"
}

// Downloaded host jars are cached here, shared across plugin projects and keyed by version + os/arch.
val devHostCacheDir = File(System.getProperty("user.home"), ".jetwhale/dev-host")

// Path of the cached host uber jar for the configured version (has a value only when hostVersion set).
val hostReleaseJar = pluginExtension.hostVersion.map { version ->
    File(devHostCacheDir, "$version/jetwhale-host-$version-$currentOsArch.jar")
}

val downloadJetWhaleHost = tasks.register("downloadJetWhaleHost") {
    group = "jetwhale"
    description = "Downloads the released JetWhale host uber jar (jetwhalePlugin.hostVersion) for the current OS."

    val versionProvider = pluginExtension.hostVersion
    val jarProvider = hostReleaseJar
    val osArch = currentOsArch
    onlyIf { versionProvider.isPresent }
    doLast {
        val version = versionProvider.get()
        val jar = jarProvider.get()
        if (jar.exists() && jar.length() > 0) return@doLast
        jar.parentFile.mkdirs()
        val url = "https://github.com/kitakkun/jetwhale/releases/download/$version/jetwhale-host-$version-$osArch.jar"
        logger.lifecycle("Downloading JetWhale host $version ($osArch) from $url")
        val tmp = File.createTempFile("jetwhale-host-", ".jar", jar.parentFile)
        java.net.URI(url).toURL().openStream().use { input -> tmp.outputStream().use(input::copyTo) }
        check(tmp.renameTo(jar)) { "Failed to move downloaded host jar into place: $jar" }
    }
}

tasks.register<JavaExec>("runJetWhaleFromRelease") {
    group = "jetwhale"
    description = "Downloads the released JetWhale host (jetwhalePlugin.hostVersion) and launches it with this plugin."

    val hostVersionProvider = pluginExtension.hostVersion
    dependsOn(stageDevPlugin, downloadJetWhaleHost)
    classpath = files(hostReleaseJar)
    mainClass.set("com.kitakkun.jetwhale.host.MainKt")

    doFirst {
        require(hostVersionProvider.isPresent) {
            "Set jetwhalePlugin.hostVersion to the released JetWhale host version to run against " +
                "(or use the in-repo `runJetWhale` task)."
        }
    }

    val devDirProvider = devPluginsDir.map { it.asFile.absolutePath }
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "-Djetwhale.devPluginsDir=${devDirProvider.get()}",
                // Self-attach for the dev hot-reload's in-place class redefinition (disabled by
                // default on JDK 9+). Structural changes still need the JetBrains Runtime.
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
