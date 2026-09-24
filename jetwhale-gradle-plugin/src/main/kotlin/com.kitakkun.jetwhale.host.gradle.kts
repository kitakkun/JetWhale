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
 * - `runJetWhaleQaAgent` — launches a headless debuggee that connects as an ordinary session, so the
 *   plugin has a session to render for, and forwards messages POSTed to its local control API on to
 *   the host plugin. Pass `-PjetwhaleQaAgentArgs="--plugin <your plugin id>"`.
 *
 * The in-repo host launcher (`runJetWhaleLocal`, which runs the local `:jetwhale-host:app` project)
 * lives in the separate, non-published `jetwhale-host-launch` convention.
 */

val pluginExtension = extensions.create("jetwhalePlugin", JetWhalePluginExtension::class.java).apply {
    pluginArchiveName.convention(project.name)
}

val packagePlugin = tasks.register<Jar>("packagePlugin") {
    group = "jetwhale"
    description = "Builds the distributable JetWhale plugin fat-jar (drop it into ~/.jetwhale/plugins/)."

    archiveBaseName.set(pluginExtension.pluginArchiveName)
    archiveClassifier.set("jetwhale-plugin")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    val thinJar = tasks.named<Jar>("jar")
    from(thinJar.map { zipTree(it.archiveFile) })

    val runtimeClasspath = configurations.named("runtimeClasspath")
    dependsOn(runtimeClasspath)
    from({ runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) } })
}

val userPluginsDir = File(System.getProperty("user.home"), ".jetwhale/plugins")

tasks.register<Copy>("installPlugin") {
    group = "jetwhale"
    description = "Installs the packaged plugin jar into ~/.jetwhale/plugins/."

    from(packagePlugin)
    into(userPluginsDir)
}

val devPluginsDir = layout.buildDirectory.dir("jetwhale/devPlugins")

val stageDevPlugin = tasks.register<Copy>("stageDevPlugin") {
    group = "jetwhale"
    description = "Copies the packaged plugin jar into the host dev plugins directory."

    from(packagePlugin)
    into(devPluginsDir)
}

// The watcher's PID passes from the run task's doFirst to stopHotStaging through this file because
// the configuration cache does not preserve a shared field across task actions.
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

val localHostJar: Provider<String> = providers.gradleProperty("jetwhaleHostJar")

// Resolve user.home as a provider OUTSIDE the combiner below. Reading it via `providers` inside the
// zip lambda would make the lambda capture the enclosing script object (its `this$0`), which the
// configuration cache cannot serialize ("cannot serialize Gradle script object references").
val userHome: Provider<String> = providers.systemProperty("user.home")

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
    onlyIf { versionProvider.isPresent && !localJarProvider.isPresent }
    doLast {
        val jar = jarProvider.get()
        val version = versionProvider.get()
        val isSnapshot = version.endsWith("-SNAPSHOT")
        val cached = jar.exists() && jar.length() > 0
        if (!isSnapshot && cached) return@doLast
        jar.parentFile.mkdirs()
        val url = "https://github.com/kitakkun/jetwhale/releases/download/$version/jetwhale-host-$version-${osArchProvider.get()}.jar"
        val etagFile = File(jar.parentFile, "${jar.name}.etag")

        fun open(method: String) = (java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 60_000
        }

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
            connection.getHeaderField("ETag")?.let { etagFile.writeText(it) }
        } finally {
            tmp.delete()
        }
    }
}

fun registerRunTask(name: String, taskDescription: String, hot: Boolean) = tasks.register<JavaExec>(name) {
    group = "jetwhale"
    description = taskDescription
    dependsOn(stageDevPlugin, downloadJetWhaleHost)

    // A provider is required: reassigning `classpath` in doFirst is lost under the configuration
    // cache, leaving an empty classpath and a "could not find main class" failure.
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

    // The host app targets Java 21 regardless of the plugin module's own toolchain. The JetBrains
    // Runtime is provisioned via Gradle toolchains; add the foojay resolver to settings to
    // auto-download it.
    javaLauncher.set(
        project.extensions.getByType(JavaToolchainService::class.java).launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
            if (hot) vendor.set(JvmVendorSpec.JETBRAINS)
        },
    )

    val devDirProvider = devPluginsDir.map { it.asFile.absolutePath }
    val sandboxDirProvider = layout.buildDirectory.dir("jetwhale-sandbox").map { it.asFile.absolutePath }
    val osName = providers.systemProperty("os.name")
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            buildList {
                add("-Djetwhale.devPluginsDir=${devDirProvider.get()}")
                add("-Djetwhale.appDataDir=${sandboxDirProvider.get()}")
                // A native crash writes its report here, next to host.log, instead of the working directory.
                add("-XX:ErrorFile=${sandboxDirProvider.get()}/logs/hs_err_pid%p.log")
                // The macOS Dock name (hover text) comes from the bundle name, which for a bare JVM
                // can only be set via -Xdock:name at launch — it is not settable at runtime.
                if (osName.getOrElse("").contains("mac", ignoreCase = true)) add("-Xdock:name=JetWhale")
                // Self-attach for the dev hot-reload's in-place class redefinition (off by default
                // on JDK 9+).
                add("-Djdk.attach.allowAttachSelf=true")
                if (hot) add("-XX:+AllowEnhancedClassRedefinition")
            }
        },
    )

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
            // On Windows a .bat must be launched through cmd.
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
            // The task action runs inside the Gradle daemon, whose streams are not the user's
            // console, so INHERIT would hide re-staging progress and compile errors.
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

    // Checked here rather than inside hostJarProvider: Gradle evaluates that provider while
    // resolving the classpath during task-graph configuration, before the jar is downloaded, and
    // fails with "Could not determine the dependencies of task". Registered last so that, with
    // doFirst's LIFO ordering, it runs before the hot-staging watcher.
    doFirst {
        val hostJar = hostJarProvider.get()
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

val qaAgentClasspath = configurations.create("jetwhaleQaAgent") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

// addProvider rather than a plain coordinate string: the version comes from the extension, whose
// value is not yet known while this script is being configured.
dependencies.addProvider(
    qaAgentClasspath.name,
    pluginExtension.qaAgentVersion.orElse(pluginExtension.hostVersion)
        .map { version -> "com.kitakkun.jetwhale:jetwhale-qa-agent:$version" },
)

tasks.register<JavaExec>("runJetWhaleQaAgent") {
    group = "jetwhale"
    description = "Runs the headless JetWhale QA agent — a debuggee for this plugin that you drive over HTTP."

    classpath = qaAgentClasspath
    mainClass.set("com.kitakkun.jetwhale.tools.qaagent.MainKt")

    val extraArgs = providers.gradleProperty("jetwhaleQaAgentArgs")
    argumentProviders.add(
        CommandLineArgumentProvider {
            extraArgs.getOrElse("").split(" ").filter { it.isNotBlank() }
        },
    )

    val agentVersion = pluginExtension.qaAgentVersion.orElse(pluginExtension.hostVersion)
    doFirst {
        check(agentVersion.isPresent) {
            "Set jetwhalePlugin.hostVersion (or jetwhalePlugin.qaAgentVersion) so the matching " +
                "JetWhale QA agent can be resolved."
        }
    }
}

tasks.named("assemble") {
    dependsOn(packagePlugin)
}

val runtimeClasspathIncoming = configurations.getByName("runtimeClasspath").incoming

val externalDependencyCoordinates: Provider<List<String>> = runtimeClasspathIncoming.artifacts.resolvedArtifacts.map { artifacts ->
    artifacts
        .mapNotNull { it.id.componentIdentifier as? org.gradle.api.artifacts.component.ModuleComponentIdentifier }
        .map { "${it.group}:${it.module}:${it.version}" }
        .distinct()
        .sorted()
}

val publishedProjectDependencyCoordinates = mutableSetOf<String>()
val bundledProjectPaths = mutableSetOf<String>()

// Walks declarations because resolving the classpath is not allowed at configuration time; only the
// project modules in the graph are needed.
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

// Publication coordinates are set in each dependency project's afterEvaluate (see the publish
// convention), so they can be read only once every project is evaluated. The sets filled here are
// read lazily by the manifest task and the artifact view.
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

val projectDependencyFiles = runtimeClasspathIncoming.artifactView {
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
