package com.kitakkun.jetwhale.host.model

import java.util.Locale

/**
 * The operating system the host process is running on, resolved once from `os.name`.
 *
 * The host is a single JVM target — the same binary runs on every platform — so the OS is only
 * knowable at runtime; there are no per-OS source sets to branch on. Read [current] instead of
 * matching `os.name` at each call site.
 */
enum class HostOs {
    MAC,
    WINDOWS,
    LINUX,
    OTHER,
    ;

    companion object {
        val current: HostOs by lazy {
            val name = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
            when {
                "mac" in name -> MAC
                "win" in name -> WINDOWS
                "nux" in name || "nix" in name -> LINUX
                else -> OTHER
            }
        }
    }
}
