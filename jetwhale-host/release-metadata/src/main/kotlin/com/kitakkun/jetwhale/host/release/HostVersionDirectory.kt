package com.kitakkun.jetwhale.host.release

import java.nio.file.Path

/** `<app data>/host/<version>/`: the host jar of each platform it was downloaded for, and its metadata. */
class HostVersionDirectory(val version: HostVersion, val path: Path) {
    val metadataFile: Path get() = path.resolve(METADATA_FILE_NAME)

    val signatureFile: Path get() = path.resolve(SIGNATURE_FILE_NAME)

    fun jarFile(platformKey: String): Path = path.resolve(hostJarName(version, platformKey))

    companion object {
        const val METADATA_FILE_NAME = "release.json"
        const val SIGNATURE_FILE_NAME = "release.json.sig"
    }
}

/** The name a release gives the host jar of [platformKey], in its assets and in a version directory. */
fun hostJarName(version: HostVersion, platformKey: String): String = "jetwhale-host-${version.name}-$platformKey.jar"
