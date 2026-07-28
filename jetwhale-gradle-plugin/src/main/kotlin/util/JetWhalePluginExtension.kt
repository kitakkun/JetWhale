package util

import org.gradle.api.provider.Property

/**
 * Configuration for the `com.kitakkun.jetwhale.host` plugin.
 *
 * Plugin authors apply the plugin and (optionally) tweak these values to control how the
 * distributable plugin jar is packaged and which released host `runJetWhale` runs against.
 */
interface JetWhalePluginExtension {
    /**
     * Base name of the packaged plugin jar (without extension). Defaults to the project name.
     * The packaged artifact is what you drop into `~/.jetwhale/plugins/`.
     */
    val pluginArchiveName: Property<String>

    /**
     * Version of the released JetWhale host to download and launch with `runJetWhale`.
     *
     * When set, `runJetWhale` fetches the matching host application (a runnable uber jar)
     * for the current OS/architecture from the GitHub release of that version. Pass
     * `-PjetwhaleHostJar=<path>` to launch a locally built host uber jar instead.
     */
    val hostVersion: Property<String>

    /**
     * Version of the JetWhale QA agent `runJetWhaleQaAgent` runs. Defaults to [hostVersion], so the
     * agent and the host it connects to speak the same protocol version.
     *
     * The QA agent is a headless debuggee: it connects as an ordinary session — giving the plugin's
     * UI a session to render for — and forwards messages you POST to its local control API on to the
     * host plugin. Set this only to pin an agent version other than the host's.
     */
    val qaAgentVersion: Property<String>
}
