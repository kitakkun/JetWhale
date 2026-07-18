import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JvmVendorSpec
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
 * - `stageDevPlugin` — copies the packaged fat-jar into a private dev directory the host watches for
 *   hot reload.
 * - `runJetWhale` — downloads the released JetWhale host (see `jetwhalePlugin.hostVersion`) for the
 *   current OS/architecture and launches it with this plugin staged for development. For live reload,
 *   run it in one terminal and `stageDevPlugin -t` in another — the host hot-reloads whenever the jar
 *   is re-staged.
 * - `runJetWhaleHot` — the same, but runs the host on the JetBrains Runtime (so structural code changes
 *   hot-reload in place too; requires a JBR toolchain) AND keeps the plugin re-staged in the background,
 *   so a single command is the whole hot-reload loop — no separate `stageDevPlugin -t` terminal needed.
 *
 * The in-repo host launcher (`runJetWhaleLocal`, which runs the local `:jetwhale-host:app` project)
 * lives in the separate, non-published `jetwhale-host-launch` convention.
 */

val pluginExtension = extensions.create("jetwhalePlugin", JetWhalePluginExtension::class.java).apply {
    pluginArchiveName.convention(project.name)
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
// stageDevPlugin: stage the packaged plugin into a dev directory the host hot-reloads from.
// ---------------------------------------------------------------------------

// A private dev directory under the module's build folder. The host watches it and hot-reloads the
// plugin jar whenever it is re-staged (e.g. by running `stageDevPlugin -t` in a separate terminal).
// NOTE: the in-repo `jetwhale-host-launch` convention reuses this exact path, so keep them in sync.
val devPluginsDir = layout.buildDirectory.dir("jetwhale/devPlugins")

// Stage the freshly packaged plugin into the dev directory.
val stageDevPlugin = tasks.register<Copy>("stageDevPlugin") {
    group = "jetwhale"
    description = "Copies the packaged plugin jar into the host dev plugins directory."

    from(packagePlugin)
    into(devPluginsDir)
}

// ---------------------------------------------------------------------------
// Continuous re-staging for runJetWhaleHot.
// ---------------------------------------------------------------------------

// runJetWhaleHot starts a background `stageDevPlugin -t` so a single command is the whole hot-reload
// loop (see registerRunTask). The watcher's PID is handed from the run task's doFirst to the finalizer
// below through this file: the configuration cache does not preserve a shared field across task actions,
// and a finalizer (unlike doLast) also runs when the host exits with a non-zero status.
val hotStagingPidFile = layout.buildDirectory.file("jetwhale/hot-staging.pid")

val stopHotStaging = tasks.register("stopHotStaging") {
    description = "Stops the background continuous-staging watcher started by runJetWhaleHot."

    val pidFileProvider = hotStagingPidFile
    doLast {
        val pidFile = pidFileProvider.get().asFile
        if (!pidFile.exists()) return@doLast
        pidFile.readText().trim().toLongOrNull()?.let { pid ->
            ProcessHandle.of(pid)
                // Guard against PID reuse: a stale pid file left by a crashed run could otherwise point
                // at an unrelated process that inherited the id. The watcher was started by this same
                // Gradle daemon (doFirst runs in it), so only act when the process is still our child.
                .filter { handle -> handle.parent().map { it.pid() == ProcessHandle.current().pid() }.orElse(false) }
                .ifPresent { handle ->
                    // Stop it gracefully first, then — if it has not exited shortly — force it so the
                    // watcher is never left running.
                    handle.descendants().forEach { it.destroy() }
                    handle.destroy()
                    runCatching { handle.onExit().get(3, java.util.concurrent.TimeUnit.SECONDS) }
                    if (handle.isAlive) {
                        handle.descendants().forEach { it.destroyForcibly() }
                        handle.destroyForcibly()
                    }
                }
        }
        pidFile.delete()
    }
}

// ---------------------------------------------------------------------------
// runJetWhale: download a released host and launch it with this plugin, for plugin
// authors developing OUTSIDE this repository (the `:jetwhale-host:app` project is not available to
// them). Set `jetwhalePlugin.hostVersion` to choose which released host to run against, or pass
// `-PjetwhaleHostJar=<path>` to launch a locally built host uber jar (useful before a release exists).
// ---------------------------------------------------------------------------

// "<os>-<arch>" of the current machine (resolved lazily), matching the host uber-jar release asset
// names. Fails clearly for OS/architectures we don't publish assets for.
val currentOsArch: Provider<String> =
    providers.systemProperty("os.name").zip(providers.systemProperty("os.arch")) { osName, archName ->
        val os = when {
            osName.contains("mac", ignoreCase = true) || osName.contains("darwin", ignoreCase = true) -> "macos"
            osName.contains("win", ignoreCase = true) -> "windows"
            osName.contains("nux", ignoreCase = true) || osName.contains("nix", ignoreCase = true) -> "linux"
            else -> error("Unsupported OS for runJetWhale: $osName")
        }
        val arch = when (archName.lowercase()) {
            "aarch64", "arm64" -> "arm64"
            "x86_64", "amd64", "x64" -> "x64"
            else -> error("Unsupported architecture for runJetWhale: $archName")
        }
        "$os-$arch"
    }

// A locally built host uber jar to launch instead of downloading (e.g. the output of
// `:jetwhale-host:app:packageUberJarForCurrentOS`). When set it overrides hostVersion.
val localHostJar: Provider<String> = providers.gradleProperty("jetwhaleHostJar")

// Resolve user.home as a provider OUTSIDE the combiner below. Reading it via `providers` inside the
// zip lambda would make the lambda capture the enclosing script object (its `this$0`), which the
// configuration cache cannot serialize ("cannot serialize Gradle script object references").
val userHome: Provider<String> = providers.systemProperty("user.home")

// Path of the cached host uber jar for the configured version (resolved lazily, value only when set).
val hostReleaseJar: Provider<File> =
    pluginExtension.hostVersion.zip(currentOsArch) { version, osArch -> version to osArch }
        .zip(userHome) { (version, osArch), home ->
            File(home, ".jetwhale/dev-host/$version/jetwhale-host-$version-$osArch.jar")
        }

val downloadJetWhaleHost = tasks.register("downloadJetWhaleHost") {
    group = "jetwhale"
    description = "Downloads the released JetWhale host uber jar (jetwhalePlugin.hostVersion) for the current OS."

    val versionProvider = pluginExtension.hostVersion
    val osArchProvider = currentOsArch
    val jarProvider = hostReleaseJar
    val localJarProvider = localHostJar
    // Nothing to download when launching a local jar, or when no version is configured.
    onlyIf { versionProvider.isPresent && !localJarProvider.isPresent }
    doLast {
        val jar = jarProvider.get()
        val version = versionProvider.get()
        val isSnapshot = version.endsWith("-SNAPSHOT")
        val cached = jar.exists() && jar.length() > 0
        // Immutable releases never change, so a present cache is always valid (no network needed).
        if (!isSnapshot && cached) return@doLast
        jar.parentFile.mkdirs()
        val url = "https://github.com/kitakkun/jetwhale/releases/download/$version/jetwhale-host-$version-${osArchProvider.get()}.jar"
        // Sidecar storing the downloaded asset's ETag, used to detect whether a SNAPSHOT changed.
        val etagFile = File(jar.parentFile, "${jar.name}.etag")

        fun open(method: String) = (java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 60_000
        }

        // SNAPSHOTs are overwritten on each publish; re-download only when the asset's ETag changed.
        if (isSnapshot && cached && etagFile.exists()) {
            val remoteETag = runCatching {
                val head = open("HEAD")
                try {
                    head.getHeaderField("ETag")
                } finally {
                    head.disconnect()
                }
            }.getOrNull()
            if (remoteETag != null && remoteETag == etagFile.readText()) {
                logger.lifecycle("JetWhale host $version is up to date; using cached jar.")
                return@doLast
            }
        }

        logger.lifecycle("Downloading JetWhale host $version from $url")
        val tmp = File.createTempFile("jetwhale-host-", ".jar", jar.parentFile)
        try {
            val connection = open("GET")
            connection.getInputStream().use { input -> tmp.outputStream().use(input::copyTo) }
            java.nio.file.Files.move(
                tmp.toPath(),
                jar.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
            // Record the new ETag so the next run can skip an unchanged download.
            connection.getHeaderField("ETag")?.let { etagFile.writeText(it) }
        } finally {
            tmp.delete()
        }
    }
}

// `runJetWhale` and `runJetWhaleHot` share the same launch config. `hot` runs the host on the
// JetBrains Runtime with enhanced class redefinition, so structural code changes (added/removed
// members, etc.) are redefined in place instead of triggering a full, state-resetting reload.
fun registerRunTask(name: String, taskDescription: String, hot: Boolean) = tasks.register<JavaExec>(name) {
    group = "jetwhale"
    description = taskDescription
    dependsOn(stageDevPlugin, downloadJetWhaleHost)

    // Resolve the host jar lazily so JavaExec reads it at execution (after downloadJetWhaleHost has
    // run). A provider is required: reassigning `classpath` in doFirst is lost under the
    // configuration cache, leaving an empty classpath and a cryptic "could not find main class".
    val hostJarProvider = providers.provider {
        when {
            localHostJar.isPresent -> File(localHostJar.get())

            pluginExtension.hostVersion.isPresent -> hostReleaseJar.get()

            else -> error(
                "Set jetwhalePlugin.hostVersion to the released JetWhale host version, or pass " +
                    "-PjetwhaleHostJar=<path> to launch a locally built host uber jar.",
            )
        }
    }
    classpath = files(hostJarProvider)
    mainClass.set("com.kitakkun.jetwhale.host.MainKt")

    // Run the host on the JetBrains Runtime (provisioned via Gradle toolchains; add the foojay
    // resolver to settings to auto-download it) so it can redefine structural changes in place.
    if (hot) {
        javaLauncher.set(
            project.extensions.getByType(JavaToolchainService::class.java).launcherFor {
                languageVersion.set(JavaLanguageVersion.of(21))
                vendor.set(JvmVendorSpec.JETBRAINS)
            },
        )
    }

    val devDirProvider = devPluginsDir.map { it.asFile.absolutePath }
    val osName = providers.systemProperty("os.name")
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            buildList {
                add("-Djetwhale.devPluginsDir=${devDirProvider.get()}")
                // The macOS Dock name (hover text) comes from the bundle name, which for a bare JVM
                // can only be set via -Xdock:name at launch — it is not settable at runtime.
                if (osName.getOrElse("").contains("mac", ignoreCase = true)) add("-Xdock:name=JetWhale")
                // Self-attach for the dev hot-reload's in-place class redefinition (off by default
                // on JDK 9+).
                add("-Djdk.attach.allowAttachSelf=true")
                // On the JetBrains Runtime, allow redefining structural changes in place too.
                if (hot) add("-XX:+AllowEnhancedClassRedefinition")
            }
        },
    )

    // Make `runJetWhaleHot` the whole hot-reload loop in one command: while the host runs in the
    // foreground (and hot-reloads from the dev directory), a background `stageDevPlugin -t` re-packages
    // and re-stages the plugin on every source change — so authors no longer need a second terminal.
    // The host is launched in the foreground here; `dependsOn(stageDevPlugin)` already staged it once
    // before this watcher takes over re-staging on change.
    if (hot) {
        // stopHotStaging is a finalizer (not doLast) so the watcher is also stopped when the host exits
        // non-zero; on an interactive Ctrl+C the watcher additionally receives the terminal's SIGINT
        // because it shares this command's process group.
        finalizedBy(stopHotStaging)

        val rootProjectDir = project.rootDir
        val stageTaskPath = if (project.path == ":") ":stageDevPlugin" else "${project.path}:stageDevPlugin"
        val osNameProvider = providers.systemProperty("os.name")
        val pidFileProvider = hotStagingPidFile

        doFirst {
            val isWindows = osNameProvider.getOrElse("").startsWith("Windows", ignoreCase = true)
            val gradlew = File(rootProjectDir, if (isWindows) "gradlew.bat" else "gradlew")
            if (!gradlew.exists()) {
                logger.warn(
                    "JetWhale: Gradle wrapper not found at $gradlew; not auto-re-staging. " +
                        "Run `./gradlew $stageTaskPath -t` in another terminal to hot-reload code changes.",
                )
                return@doFirst
            }
            // On Windows a .bat must be launched through cmd; elsewhere the wrapper is executed directly.
            val command = buildList {
                if (isWindows) addAll(listOf("cmd", "/c"))
                add(gradlew.absolutePath)
                addAll(listOf(stageTaskPath, "-t", "--console=plain"))
            }
            logger.lifecycle("JetWhale: continuously re-staging the plugin on source changes (${command.joinToString(" ")}).")
            val process = ProcessBuilder(command)
                .directory(rootProjectDir)
                .redirectErrorStream(true)
                .start()
            val pidFile = pidFileProvider.get().asFile
            pidFile.parentFile.mkdirs()
            pidFile.writeText(process.pid().toString())
            // The task action runs inside the Gradle daemon, whose streams are not the user's console,
            // so a raw INHERIT would hide re-staging progress and (critically) any compile errors.
            // Pump the watcher's output through this task's logger so it surfaces on the console.
            val watcherLogger = logger
            Thread(
                {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { watcherLogger.lifecycle("[stageDevPlugin] $it") }
                    }
                },
                "jetwhale-hot-staging-watcher",
            ).also { it.isDaemon = true }.start()
        }
    }

    // Verify the resolved host jar exists at EXECUTION time — after downloadJetWhaleHost (a dependency)
    // has had a chance to download it. Checking this inside hostJarProvider instead would evaluate it
    // while Gradle resolves the classpath's dependencies during task-graph configuration, before the
    // jar is downloaded, failing with the cryptic "Could not determine the dependencies of task".
    // Registered last so that, with doFirst's LIFO ordering, it runs before the hot-staging watcher above.
    doFirst {
        val hostJar = hostJarProvider.get()
        // Require a real, non-empty file: a directory (e.g. a misconfigured -PjetwhaleHostJar) or an
        // empty file would otherwise pass and fail later with a cryptic JVM "could not find main class".
        check(hostJar.isFile && hostJar.length() > 0) {
            "JetWhale host jar is missing, empty, or not a regular file: $hostJar"
        }
    }
}

registerRunTask(
    name = "runJetWhale",
    taskDescription = "Downloads the released JetWhale host (jetwhalePlugin.hostVersion) and launches it with this plugin.",
    hot = false,
)
registerRunTask(
    name = "runJetWhaleHot",
    taskDescription = "Like runJetWhale, but runs the host on the JetBrains Runtime (structural changes hot-reload in " +
        "place; requires a JBR toolchain) and auto re-stages the plugin in the background — the whole hot-reload loop in one command.",
    hot = true,
)

// Make sure `packagePlugin` participates in the standard `assemble`/`build` lifecycle so authors get
// the distributable artifact without invoking the task by name.
tasks.named("assemble") {
    dependsOn(packagePlugin)
}

// ---------------------------------------------------------------------------
// Maven publishing: publish a thin plugin jar plus a dependency manifest.
// ---------------------------------------------------------------------------

// The published plugin artifact deliberately does NOT bundle third-party dependencies (publishing a
// fat-jar would redistribute them, with the license obligations that entails, and would bake the
// build machine's platform-specific artifacts into a supposedly universal jar). Instead the
// published jar carries:
//
// - the module's own classes and resources (plus, as a fallback, the classes of any in-build
//   project dependency that is NOT itself published — a published project dependency has Maven
//   coordinates of its own, taken from its `publishing` configuration exactly as the generated POM
//   does, and is listed in the manifest like any external dependency), and
// - `META-INF/jetwhale/dependencies.txt` — the flat list of runtime dependencies as
//   `group:artifact:version` lines, exactly as Gradle resolved them at build time.
//
// The host downloads the listed jars itself when installing the plugin from Maven, so no full
// dependency-resolution logic (parent POMs, BOMs, Gradle module metadata…) is ever needed at
// runtime, and nothing third-party is redistributed by the plugin author.

val runtimeElementsIncoming = configurations.getByName("runtimeClasspath").incoming

// External module dependencies -> lockfile lines. Resolved lazily and configuration-cache safe.
val externalDependencyCoordinates: Provider<List<String>> = runtimeElementsIncoming.artifacts.resolvedArtifacts.map { artifacts ->
    artifacts
        .mapNotNull { it.id.componentIdentifier as? org.gradle.api.artifacts.component.ModuleComponentIdentifier }
        .map { "${it.group}:${it.module}:${it.version}" }
        .distinct()
        .sorted()
}

// In-build project dependencies: published ones are trusted to be fetchable by their publication
// coordinates (the same source the generated POM uses — for KMP modules that is the `jvm`
// publication, e.g. `protocol-jvm`); unpublished ones are bundled into the jar as a fallback.
// Both sets are filled once every project is evaluated (publication coordinates are configured in
// the dependency projects' own afterEvaluate blocks, so they cannot be read earlier).
val publishedProjectDependencyCoordinates = mutableSetOf<String>()
val bundledProjectPaths = mutableSetOf<String>()

// Declared-dependency configurations that can carry project dependencies, for plain-JVM and KMP
// modules alike. Walking declarations (instead of resolving the classpath, which is not allowed at
// configuration time) is safe here: we only need to identify the project modules in the graph.
val projectDependencyConfigurationNames = listOf(
    "api",
    "implementation",
    "runtimeOnly",
    "commonMainApi",
    "commonMainImplementation",
    "jvmMainApi",
    "jvmMainImplementation",
)

fun collectProjectDependencies(from: Project, seenPaths: MutableSet<String>) {
    projectDependencyConfigurationNames.forEach { configurationName ->
        from.configurations.findByName(configurationName)
            ?.dependencies
            ?.withType(org.gradle.api.artifacts.ProjectDependency::class.java)
            ?.forEach { dependency ->
                val depProject = runCatching { from.project(dependency.path) }.getOrNull() ?: return@forEach
                if (seenPaths.add(depProject.path)) collectProjectDependencies(depProject, seenPaths)
            }
    }
}

gradle.projectsEvaluated {
    val seenPaths = mutableSetOf<String>()
    collectProjectDependencies(project, seenPaths)
    seenPaths.forEach { path ->
        val depProject = project.project(path)
        val publications = depProject.extensions
            .findByType(org.gradle.api.publish.PublishingExtension::class.java)
            ?.publications
            ?.withType(org.gradle.api.publish.maven.MavenPublication::class.java)
        // KMP modules publish the JVM variant under the `jvm` publication; plain JVM modules
        // publish a single publication (commonly `maven`).
        val publication = publications?.findByName("jvm") ?: publications?.singleOrNull()
        if (publication != null) {
            publishedProjectDependencyCoordinates +=
                "${publication.groupId}:${publication.artifactId}:${publication.version}"
        } else {
            logger.info(
                "packageMavenPlugin: project dependency $path has no Maven publication; " +
                    "bundling its classes into the plugin jar.",
            )
            bundledProjectPaths += path
        }
    }
}

val projectDependencyFiles = runtimeElementsIncoming.artifactView {
    componentFilter {
        it is org.gradle.api.artifacts.component.ProjectComponentIdentifier && it.projectPath in bundledProjectPaths
    }
}.files

val generatePluginDependencyManifest = tasks.register("generatePluginDependencyManifest") {
    group = "jetwhale"
    description = "Writes the plugin's external runtime dependencies as a Maven-coordinates manifest."

    val projectCoordinates = publishedProjectDependencyCoordinates
    val coordinates = externalDependencyCoordinates.map { external ->
        (external + projectCoordinates).distinct().sorted()
    }
    val outputFile = layout.buildDirectory.file("jetwhale/dependencies.txt")
    inputs.property("coordinates", coordinates)
    outputs.file(outputFile)

    doLast {
        outputFile.get().asFile.writeText(coordinates.get().joinToString("\n", postfix = "\n"))
    }
}

val packageMavenPlugin = tasks.register<Jar>("packageMavenPlugin") {
    group = "jetwhale"
    description = "Builds the publishable plugin jar (own + project-dependency classes, external deps as a manifest)."

    archiveBaseName.set(pluginExtension.pluginArchiveName)
    archiveClassifier.set("jetwhale-maven-plugin")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    val thinJar = tasks.named<Jar>("jar")
    from(thinJar.map { zipTree(it.archiveFile) })
    from({ projectDependencyFiles.map { zipTree(it) } })
    from(generatePluginDependencyManifest) {
        into("META-INF/jetwhale")
    }
}

// When the plugin author also applies a Maven publishing plugin, replace the outgoing jar of the
// java component with the `packageMavenPlugin` jar (classifier cleared, so it publishes as the
// plain `<artifactId>-<version>.jar`): the JetWhale host's Install-from-Maven feature downloads
// exactly that main artifact.
pluginManager.withPlugin("maven-publish") {
    listOf("apiElements", "runtimeElements").forEach { configurationName ->
        configurations.named(configurationName) {
            outgoing.artifacts.clear()
            outgoing.artifact(packageMavenPlugin) {
                classifier = null
            }
        }
    }
}
