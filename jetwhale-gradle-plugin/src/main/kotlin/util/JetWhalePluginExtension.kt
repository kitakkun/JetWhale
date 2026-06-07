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
}
