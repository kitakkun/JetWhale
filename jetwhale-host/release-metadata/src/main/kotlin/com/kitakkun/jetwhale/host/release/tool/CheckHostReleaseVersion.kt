package com.kitakkun.jetwhale.host.release.tool

import com.kitakkun.jetwhale.host.release.HostVersion
import kotlin.system.exitProcess

/**
 * Exits with 1 when its one argument is not a release version, so the release job refuses a tag the
 * launcher and the host could not order before it builds anything.
 */
fun main(args: Array<String>) {
    val versionName = args.singleOrNull()
    if (versionName == null || HostVersion.parse(versionName) == null) {
        System.err.println(
            "$versionName is not a release version: MAJOR.MINOR.PATCH, optionally followed by -alphaN, -betaN or -rcN with N from 1 to 199.",
        )
        exitProcess(1)
    }
}
