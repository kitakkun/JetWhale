package com.kitakkun.jetwhale.host.release

/**
 * The `os-arch` key of a machine (`macos-arm64`, `linux-x64`, `windows-x64`, …), as the release
 * assets and the Gradle plugin's `downloadJetWhaleHost` name it. Null for an OS or architecture no
 * release could be built for.
 *
 * @param osName The `os.name` system property.
 * @param osArch The `os.arch` system property.
 */
fun hostPlatformKey(osName: String, osArch: String): String? {
    val os = when {
        osName.contains("mac", ignoreCase = true) || osName.contains("darwin", ignoreCase = true) -> "macos"
        osName.contains("win", ignoreCase = true) -> "windows"
        osName.contains("nux", ignoreCase = true) || osName.contains("nix", ignoreCase = true) -> "linux"
        else -> return null
    }
    val arch = when (osArch.lowercase()) {
        "aarch64", "arm64" -> "arm64"
        "x86_64", "amd64", "x64" -> "x64"
        else -> return null
    }
    return "$os-$arch"
}
