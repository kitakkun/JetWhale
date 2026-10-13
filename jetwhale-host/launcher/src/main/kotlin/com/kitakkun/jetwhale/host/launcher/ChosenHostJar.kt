package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.HostReleaseMetadata
import com.kitakkun.jetwhale.host.release.HostVersion
import java.nio.file.Path

/**
 * The host jar the launcher is about to start: one that verified, or the bundled one.
 *
 * @property version The host version the jar is a build of.
 * @property isBundled Whether this is the jar installed with the package. The package vouches for it,
 * so it is not verified again; its version is also the floor, never deleted and never set aside.
 */
class ChosenHostJar(
    val version: HostVersion,
    val metadata: HostReleaseMetadata,
    val path: Path,
    val isBundled: Boolean,
)
