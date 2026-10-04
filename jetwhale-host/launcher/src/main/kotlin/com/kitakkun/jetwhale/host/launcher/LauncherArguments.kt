package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.LauncherContract

/**
 * What the launcher was started with: `--after <pid>` and `--retry <version>` for itself, and
 * everything else for the host.
 */
class LauncherArguments(
    val hostArguments: List<String>,
    val afterPid: Long?,
    val retryVersion: String?,
) {
    val headless: Boolean get() = HEADLESS_ARGUMENT in hostArguments

    companion object {
        private const val HEADLESS_ARGUMENT = "--headless"

        fun parse(arguments: List<String>): LauncherArguments {
            val hostArguments = mutableListOf<String>()
            var afterPid: Long? = null
            var retryVersion: String? = null
            val iterator = arguments.iterator()
            while (iterator.hasNext()) {
                when (val argument = iterator.next()) {
                    LauncherContract.AFTER_ARGUMENT -> afterPid = iterator.nextOrNull()?.toLongOrNull()
                    LauncherContract.RETRY_ARGUMENT -> retryVersion = iterator.nextOrNull()
                    else -> hostArguments += argument
                }
            }
            return LauncherArguments(hostArguments, afterPid, retryVersion)
        }
    }
}

private fun Iterator<String>.nextOrNull(): String? = if (hasNext()) next() else null
