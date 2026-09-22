package com.kitakkun.jetwhale.host.idea

import java.net.URL
import java.net.URLClassLoader

/**
 * Loads the host's jars child-first. The JDK's own classes come from the platform loader, so Swing
 * and AWT types are shared with the IDE and a [javax.swing.JComponent] built here can be added to
 * an IDE tool window. Everything else is looked up in the host jars before [parent], so Kotlin,
 * coroutines, Compose and the rest resolve to the versions the host was built against, never to the
 * IDE's copies. [parent] (the plugin's own loader) is the fallback, which keeps IDE APIs reachable.
 */
internal class HostClassLoader(jars: Array<URL>, parent: ClassLoader) : URLClassLoader(jars, parent) {
    private val platformLoader: ClassLoader = ClassLoader.getPlatformClassLoader()

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        synchronized(getClassLoadingLock(name)) {
            findLoadedClass(name)?.let { return it.also { c -> if (resolve) resolveClass(c) } }
            val platformClass = try {
                platformLoader.loadClass(name)
            } catch (_: ClassNotFoundException) {
                null
            }
            if (platformClass != null) return platformClass
            val ownClass = try {
                findClass(name)
            } catch (_: ClassNotFoundException) {
                null
            }
            if (ownClass != null) return ownClass.also { if (resolve) resolveClass(it) }
            return super.loadClass(name, resolve)
        }
    }

    override fun getResource(name: String): URL? = findResource(name) ?: super.getResource(name)
}
