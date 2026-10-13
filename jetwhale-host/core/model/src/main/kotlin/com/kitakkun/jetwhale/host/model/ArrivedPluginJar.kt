package com.kitakkun.jetwhale.host.model

/**
 * A jar that appeared in the plugins directory while the host was running and is waiting for the
 * user to load it or put it off. Only what can be read without executing the jar is described.
 *
 * @property declaredPlugins the plugins its manifest declares; empty when the manifest could not be
 *   read, in which case [unreadableReason] says why.
 * @property replacedPlugins the plugins currently running from the jar this one overwrote; empty for
 *   a jar that is new to the directory.
 * @property loadFailure why the jar did not load after the user approved it; it stays offered so
 *   the failure is seen.
 */
data class ArrivedPluginJar(
    val jarPath: String,
    val sizeBytes: Long,
    val sha256: String,
    val declaredPlugins: List<DeclaredPlugin>,
    val unreadableReason: String?,
    val replacedPlugins: List<DeclaredPlugin>,
    val loadFailure: String?,
) {
    val fileName: String get() = jarPath.substringAfterLast('/').substringAfterLast('\\')
}

/** A plugin as its jar's manifest names it. */
data class DeclaredPlugin(
    val pluginId: String,
    val pluginName: String,
    val version: String,
)
