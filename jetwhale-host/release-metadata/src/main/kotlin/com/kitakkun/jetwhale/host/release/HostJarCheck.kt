package com.kitakkun.jetwhale.host.release

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.security.MessageDigest

/** How a jar on disk compares with the [HostPlatformRelease] it should be. */
sealed interface HostJarCheck {
    data object Matches : HostJarCheck

    data object Missing : HostJarCheck

    data class SizeMismatch(val expected: Long, val actual: Long) : HostJarCheck

    data class HashMismatch(val expected: String, val actual: String) : HostJarCheck
}

/**
 * Checks [jar] against this entry: its size first, so a truncated download fails without being
 * hashed, then its SHA-256.
 */
fun HostPlatformRelease.check(jar: Path): HostJarCheck {
    val actualSize = try {
        Files.size(jar)
    } catch (_: NoSuchFileException) {
        return HostJarCheck.Missing
    }
    if (actualSize != size) return HostJarCheck.SizeMismatch(expected = size, actual = actualSize)
    val actualHash = sha256Hex(jar)
    if (actualHash != sha256) return HostJarCheck.HashMismatch(expected = sha256, actual = actualHash)
    return HostJarCheck.Matches
}

/** The lowercase hex SHA-256 of [file], as the metadata spells it. */
internal fun sha256Hex(file: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(file).use { input ->
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
