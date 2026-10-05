package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.HeldLock
import com.kitakkun.jetwhale.host.release.LockFiles
import java.nio.file.Path

/**
 * `launch.lock` as one launch holds it: taken on creation, released once the started host has
 * published its record, and taken again before the launch writes `launcher-state.json` or starts
 * another host.
 */
internal class LaunchLock(private val lockFiles: LockFiles, private val path: Path) {
    private var held: HeldLock? = lockFiles.lock(path)

    fun release() {
        held?.close()
        held = null
    }

    fun ensureHeld() {
        if (held == null) held = lockFiles.lock(path)
    }
}
