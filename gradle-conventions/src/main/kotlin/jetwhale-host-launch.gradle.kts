import org.gradle.process.CommandLineArgumentProvider

/**
 * In-repo-only companion to the published `com.kitakkun.jetwhale.host` plugin.
 *
 * Adds `runJetWhaleLocal`, which launches the locally built host project (`:jetwhale-host:app`) with
 * this plugin staged for hot reload, and `runJetWhaleQaAgentLocal`, which runs the locally built QA
 * agent (`:tools:qa-agent`) against it. Both live here, and are NOT published, because they depend on
 * this repository's own projects — which external plugin authors don't have. They use `runJetWhale`
 * and `runJetWhaleQaAgent` from the published plugin instead, which resolve released artifacts.
 *
 * Apply this alongside `com.kitakkun.jetwhale.host`: it reuses that plugin's `stageDevPlugin` task and its
 * dev plugins directory.
 */

val devPluginsDir = layout.buildDirectory.dir("jetwhale/devPlugins")

val jetwhaleHostRuntime = configurations.create("jetwhaleHostRuntime") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
dependencies.add("jetwhaleHostRuntime", dependencies.project(":jetwhale-host:app"))

tasks.register<JavaExec>("runJetWhaleLocal") {
    group = "jetwhale"
    description = "Launches the local JetWhale host project with this plugin loaded for development (hot reload)."

    // Referenced by name, not tasks.named, so the order the plugins are applied in does not matter.
    dependsOn("stageDevPlugin")

    classpath = jetwhaleHostRuntime
    mainClass.set("com.kitakkun.jetwhale.host.MainKt")

    // The host app targets Java 21; without this the launcher defaults to the plugin module's
    // 17 toolchain and fails on the host's class files.
    javaLauncher.set(
        project.extensions.getByType(JavaToolchainService::class.java).launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
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

val jetwhaleQaAgentRuntime = configurations.create("jetwhaleQaAgentRuntime") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
dependencies.add("jetwhaleQaAgentRuntime", dependencies.project(":tools:qa-agent"))

tasks.register<JavaExec>("runJetWhaleQaAgentLocal") {
    group = "jetwhale"
    description = "Runs the locally built JetWhale QA agent — a headless debuggee for this plugin, driven over HTTP."

    classpath = jetwhaleQaAgentRuntime
    mainClass.set("com.kitakkun.jetwhale.tools.qaagent.MainKt")

    val extraArgs = providers.gradleProperty("jetwhaleQaAgentArgs")
    argumentProviders.add(
        CommandLineArgumentProvider {
            extraArgs.getOrElse("").split(" ").filter { it.isNotBlank() }
        },
    )
}
