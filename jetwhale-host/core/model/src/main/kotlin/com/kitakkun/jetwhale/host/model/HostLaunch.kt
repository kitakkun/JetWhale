package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.host.release.HostVersion
import java.nio.file.Path

/** How this host process was started, which decides whether it can update itself. */
sealed interface HostLaunch {
    /**
     * By the launcher of an installed package, which starts the newest verified host version and can
     * restart into a newer one.
     *
     * @property launcherContract The launcher contract the launcher implements.
     * @property launcherExecutable What the host starts to restart through the launcher; null when the
     * launcher could not tell its own path.
     * @property hostDirectory `<app data>/host`, where downloaded versions go.
     * @property setAsideVersion The version this launch set aside because it failed its first starts.
     * @property arguments The arguments the host was started with, which a restart passes on.
     */
    data class ByLauncher(
        val launcherContract: Int,
        val launcherExecutable: String?,
        val hostDirectory: Path,
        val setAsideVersion: HostVersion?,
        val arguments: List<String>,
    ) : HostLaunch

    /** Any other way: `java -jar` on a host jar, the Gradle tasks, or the IDE plugin. */
    data object Standalone : HostLaunch
}
