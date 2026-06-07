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
 * - `stageDevPlugin` — copies the packaged fat-jar into a private dev directory the host watches for
 *   hot reload.
 * - `runJetWhaleFromRelease` — downloads the released JetWhale host (see `jetwhalePlugin.hostVersion`)
 *   for the current OS/architecture and launches it with this plugin staged for development. For live
 *   reload, run it in one terminal and `stageDevPlugin -t` in another — the host hot-reloads whenever
 *   the jar is re-staged.
 *
 * The in-repo host launcher (`runJetWhale`, which runs the local `:jetwhale-host:app` project) lives
 * in the separate, non-published `jetwhale-host-launch` convention.
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
// runJetWhaleFromRelease: download a released host and launch it with this plugin, for plugin
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
            else -> error("Unsupported OS for runJetWhaleFromRelease: $osName")
        }
        val arch = when (archName.lowercase()) {
            "aarch64", "arm64" -> "arm64"
            "x86_64", "amd64", "x64" -> "x64"
            else -> error("Unsupported architecture for runJetWhaleFromRelease: $archName")
        }
        "$os-$arch"
    }

// A locally built host uber jar to launch instead of downloading (e.g. the output of
// `:jetwhale-host:app:packageUberJarForCurrentOS`). When set it overrides hostVersion.
val localHostJar: Provider<String> = providers.gradleProperty("jetwhaleHostJar")

// Path of the cached host uber jar for the configured version (resolved lazily, value only when set).
val hostReleaseJar: Provider<File> =
    pluginExtension.hostVersion.zip(currentOsArch) { version, osArch ->
        File(
            providers.systemProperty("user.home").get(),
            ".jetwhale/dev-host/$version/jetwhale-host-$version-$osArch.jar",
        )
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

tasks.register<JavaExec>("runJetWhaleFromRelease") {
    group = "jetwhale"
    description = "Downloads the released JetWhale host (jetwhalePlugin.hostVersion) and launches it with this plugin."

    val hostVersionProvider = pluginExtension.hostVersion
    val releaseJarProvider = hostReleaseJar
    val localJarProvider = localHostJar
    dependsOn(stageDevPlugin, downloadJetWhaleHost)

    // Resolve the host jar lazily so JavaExec reads it at execution (after downloadJetWhaleHost has
    // run). A provider is required: reassigning `classpath` in doFirst is lost under the configuration
    // cache, leaving an empty classpath and a cryptic "could not find main class" failure.
    val hostJarProvider = providers.provider {
        val hostJar = when {
            localJarProvider.isPresent -> File(localJarProvider.get())

            hostVersionProvider.isPresent -> releaseJarProvider.get()

            else -> error(
                "Set jetwhalePlugin.hostVersion to the released JetWhale host version, or pass " +
                    "-PjetwhaleHostJar=<path> to launch a local host jar (or use the in-repo `runJetWhale`).",
            )
        }
        check(hostJar.exists()) { "JetWhale host jar not found: $hostJar" }
        hostJar
    }
    classpath = files(hostJarProvider)
    mainClass.set("com.kitakkun.jetwhale.host.MainKt")

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
