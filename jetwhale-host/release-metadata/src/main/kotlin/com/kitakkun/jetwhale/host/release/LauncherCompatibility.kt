package com.kitakkun.jetwhale.host.release

/**
 * Which releases a launcher with [capabilities] can run. The launcher does not start any other, and
 * the host does not offer one.
 */
class LauncherCompatibility(private val capabilities: LauncherCapabilities) {
    /** Why [metadata]'s release cannot run on this launcher, or null when it can. */
    fun refusalOf(metadata: HostReleaseMetadata): HostReleaseRefusal? {
        if (metadata.launcherContract > capabilities.contract) {
            return HostReleaseRefusal.NeedsNewerLauncher(metadata.launcherContract)
        }
        if (metadata.runtime.javaFeatureVersion > capabilities.javaFeatureVersion) {
            return HostReleaseRefusal.NeedsNewerJava(metadata.runtime.javaFeatureVersion)
        }
        val missingModules = metadata.runtime.modules.filterNot(capabilities.modules::contains)
        if (missingModules.isNotEmpty()) {
            return HostReleaseRefusal.MissingModules(missingModules)
        }
        if (capabilities.platformKey !in metadata.platforms) {
            return HostReleaseRefusal.NoBuildForPlatform(capabilities.platformKey)
        }
        val jvmArgs = metadata.jvmArgsFor(capabilities.platformKey)
        val disallowedArgument = jvmArgs.firstOrNull { !isAllowedHostJvmArgument(it) }
        if (disallowedArgument != null) {
            return HostReleaseRefusal.DisallowedJvmArgument(disallowedArgument)
        }
        val missingArgument = jvmArgs.firstOrNull { systemPropertyOf(it) == null && it !in capabilities.jvmArguments }
        if (missingArgument != null) {
            return HostReleaseRefusal.MissingJvmArgument(missingArgument)
        }
        return null
    }
}

/** Why a launcher does not start a release, and the host does not offer it. */
sealed interface HostReleaseRefusal {
    data class NeedsNewerLauncher(val contract: Int) : HostReleaseRefusal

    data class NeedsNewerJava(val javaFeatureVersion: Int) : HostReleaseRefusal

    data class MissingModules(val modules: List<String>) : HostReleaseRefusal

    data class NoBuildForPlatform(val platformKey: String) : HostReleaseRefusal

    /** The release asks for a JVM argument outside the forms [isAllowedHostJvmArgument] accepts. */
    data class DisallowedJvmArgument(val argument: String) : HostReleaseRefusal

    /**
     * The release asks for a JVM argument that only the JVM's start can take, and the launcher's JVM
     * did not start with it.
     */
    data class MissingJvmArgument(val argument: String) : HostReleaseRefusal
}
