package com.kitakkun.jetwhale.host.idea

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import javax.swing.JComponent
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries

/**
 * Owns the isolated classloader and the single host instance inside it for the life of the IDE
 * process. Everything past this class runs in that loader; this side only ever sees JDK types.
 */
@Service(Service.Level.APP)
class JetWhaleHostService : Disposable {
    private val hostClassLoader: HostClassLoader
    private val host: AutoCloseable

    init {
        // This jar is installed at <plugin>/lib/<name>.jar and the host next to it under <plugin>/host.
        val ownJar = PathManager.getJarPathForClass(JetWhaleHostService::class.java)
            ?: error("JetWhale plugin jar location is unknown")
        val hostDirectory = Path(ownJar).parent.parent.resolve("host")
        val jars = hostDirectory.listDirectoryEntries("*.jar").map { it.toUri().toURL() }
        hostClassLoader = HostClassLoader(jars.toTypedArray(), JetWhaleHostService::class.java.classLoader)
        host = inHostLoader {
            hostClassLoader.loadClass(HOST_ENTRY_CLASS).getConstructor().newInstance() as AutoCloseable
        }
    }

    fun createToolWindowContent(): JComponent = inHostLoader {
        host.javaClass.getMethod("createToolWindowContent").invoke(host) as JComponent
    }

    override fun dispose() {
        inHostLoader { host.close() }
        hostClassLoader.close()
    }

    // Libraries that discover services through the thread's context loader (SLF4J, Ktor engines)
    // must find the host's copies, not the IDE's.
    private inline fun <T> inHostLoader(block: () -> T): T {
        val thread = Thread.currentThread()
        val previous = thread.contextClassLoader
        thread.contextClassLoader = hostClassLoader
        try {
            return block()
        } finally {
            thread.contextClassLoader = previous
        }
    }

    private companion object {
        const val HOST_ENTRY_CLASS = "com.kitakkun.jetwhale.host.idea.IdeHost"
    }
}
