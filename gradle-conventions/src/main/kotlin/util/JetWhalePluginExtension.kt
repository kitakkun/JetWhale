package util

import org.gradle.api.provider.Property

/**
 * Configuration for the `jetwhale-plugin` convention.
 *
 * Plugin authors apply the `jetwhale-plugin` convention and (optionally) tweak these values to
 * control how the distributable plugin jar is packaged and how `runJetWhale` launches the host.
 */
interface JetWhalePluginExtension {
    /**
     * Base name of the packaged plugin jar (without extension). Defaults to the project name.
     * The packaged artifact is what you drop into `~/.jetwhale/plugins/`.
     */
    val pluginArchiveName: Property<String>

    /**
     * Gradle project path of the JetWhale host application used by `runJetWhale`.
     *
     * Defaults to `:jetwhale-host:app`. The referenced project must apply the Compose Desktop
     * application plugin and expose `com.kitakkun.jetwhale.host.MainKt` as its main class.
     *
     * Note: this assumes the host is available as a project in the same Gradle build. A full Maven
     * distribution of the host (so external builds can resolve it) is out of scope for now.
     */
    val hostApplicationProject: Property<String>
}
