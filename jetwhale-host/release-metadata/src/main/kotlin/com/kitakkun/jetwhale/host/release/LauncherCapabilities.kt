package com.kitakkun.jetwhale.host.release

/**
 * What an installed launcher can run: the contract it implements and the runtime it was installed
 * with. Neither changes without a reinstall.
 *
 * @property modules The runtime modules a host can use ([runtimeModulesVisibleToHost]).
 * @property platformKey The machine's `os-arch` key ([hostPlatformKey]).
 * @property jvmArguments The arguments the launcher's JVM started with. A host runs in that JVM, so
 * every argument a release asks for that does not set a system property has to be among them.
 */
data class LauncherCapabilities(
    val contract: Int,
    val javaFeatureVersion: Int,
    val modules: Set<String>,
    val platformKey: String,
    val jvmArguments: List<String>,
)

/**
 * The modules of this runtime that a host's classes can use: those of the boot layer defined to the
 * boot or the platform class loader. A host's class loader has the platform class loader as its
 * parent, so a module the runtime defines to the application class loader, such as `jdk.attach`, is
 * out of its reach even though the image holds it.
 */
fun runtimeModulesVisibleToHost(): Set<String> = ModuleLayer.boot().modules()
    .filter { it.classLoader == null || it.classLoader == ClassLoader.getPlatformClassLoader() }
    .mapTo(mutableSetOf(), Module::getName)
