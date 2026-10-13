package com.kitakkun.jetwhale.plugins.mirror.host

import org.junit.Assume.assumeFalse

private val onWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

/** Skips the test on Windows, which cannot launch the `/bin/sh` scripts that stand in for the tools. */
internal fun assumeShellScriptsLaunch() = assumeFalse("the fake tools are /bin/sh scripts, which Windows cannot launch", onWindows)

/** Skips the test on Windows, where java.io.File cannot take away the permission the test relies on losing. */
internal fun assumePosixPermissions() = assumeFalse("Windows does not enforce the permission java.io.File clears here", onWindows)
