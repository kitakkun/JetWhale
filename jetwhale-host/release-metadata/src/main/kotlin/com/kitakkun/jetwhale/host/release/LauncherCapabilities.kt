package com.kitakkun.jetwhale.host.release

/**
 * What an installed launcher can run: the contract it implements and the runtime it was installed
 * with. Neither changes without a reinstall.
 *
 * @property modules Every module in the launcher's runtime image.
 * @property platformKey The machine's `os-arch` key ([hostPlatformKey]).
 */
data class LauncherCapabilities(
    val contract: Int,
    val javaFeatureVersion: Int,
    val modules: Set<String>,
    val platformKey: String,
)

/** Why a launcher does not start a release, and the host does not offer it. */
sealed interface HostReleaseRefusal {
    data class NeedsNewerLauncher(val contract: Int) : HostReleaseRefusal

    data class NeedsNewerJava(val javaFeatureVersion: Int) : HostReleaseRefusal

    data class MissingModules(val modules: List<String>) : HostReleaseRefusal

    data class NoBuildForPlatform(val platformKey: String) : HostReleaseRefusal

    /** The release asks for a JVM argument outside the forms [isAllowedHostJvmArgument] accepts. */
    data class DisallowedJvmArgument(val argument: String) : HostReleaseRefusal
}
