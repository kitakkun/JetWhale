package com.kitakkun.jetwhale.host.data

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/**
 * Locks down permissions on TLS material so only the owning user can read it. The keystore holds the
 * CA private key, so on multi-user machines the files must not be world/group readable.
 *
 * POSIX filesystems get exact mode bits via [Files.setPosixFilePermissions]. Non-POSIX filesystems
 * (e.g. Windows/NTFS) do not support POSIX modes, so we fall back to the [File] read/write/execute
 * toggles: first strip access for everyone, then re-grant it to the owner only.
 */
internal object FilePermissions {
    /** Restrict a directory to owner-only access (POSIX 0700). */
    fun restrictToOwnerDirectory(file: File) {
        val posix = setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
        )
        applyOwnerOnly(file, posix, executable = true)
    }

    /** Restrict a regular file to owner read/write only (POSIX 0600). */
    fun restrictToOwnerFile(file: File) {
        val posix = setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
        )
        applyOwnerOnly(file, posix, executable = false)
    }

    private fun applyOwnerOnly(file: File, posixPermissions: Set<PosixFilePermission>, executable: Boolean) {
        val path = file.toPath()
        val supportsPosix = Files.getFileStore(path).supportsFileAttributeView(java.nio.file.attribute.PosixFileAttributeView::class.java)
        if (supportsPosix) {
            Files.setPosixFilePermissions(path, posixPermissions)
        } else {
            // Non-POSIX fallback: revoke access for everyone, then re-grant to the owner only.
            file.setReadable(false, false)
            file.setWritable(false, false)
            file.setExecutable(false, false)
            file.setReadable(true, true)
            file.setWritable(true, true)
            if (executable) {
                file.setExecutable(true, true)
            }
        }
    }
}
