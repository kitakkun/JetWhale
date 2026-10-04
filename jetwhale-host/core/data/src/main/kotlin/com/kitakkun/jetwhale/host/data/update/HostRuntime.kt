package com.kitakkun.jetwhale.host.data.update

import com.kitakkun.jetwhale.host.release.hostPlatformKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import java.lang.module.ModuleFinder

/**
 * The runtime this host runs on, which is the launcher's: what a release may ask of it.
 *
 * @property platformKey The machine's `os-arch` key, or null for a machine no release is built for.
 */
class HostRuntime(
    val javaFeatureVersion: Int,
    val modules: Set<String>,
    val platformKey: String?,
)

@ContributesTo(AppScope::class)
interface HostRuntimeProvider {
    @Provides
    fun provideHostRuntime(): HostRuntime = HostRuntime(
        javaFeatureVersion = Runtime.version().feature(),
        modules = ModuleFinder.ofSystem().findAll().map { it.descriptor().name() }.toSet(),
        platformKey = hostPlatformKey(System.getProperty("os.name"), System.getProperty("os.arch")),
    )
}
