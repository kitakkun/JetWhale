package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.HostReleaseMetadata
import com.kitakkun.jetwhale.host.release.HostVersion
import java.nio.file.Path

/** A host version the launcher is about to start: one that verified, or the bundled one. */
class ChosenHostVersion(
    val version: HostVersion,
    val metadata: HostReleaseMetadata,
    val jar: Path,
    val isBundled: Boolean,
)
