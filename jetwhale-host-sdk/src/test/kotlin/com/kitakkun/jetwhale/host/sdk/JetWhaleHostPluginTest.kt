package com.kitakkun.jetwhale.host.sdk

import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(InternalJetWhaleHostApi::class)
class JetWhaleHostPluginTest {
    @Test
    fun `an instance bound to the host session knows it runs there`() {
        val plugin = SessionKindProbePlugin().apply { bindSessionKind(isInHostSession = true) }

        assertTrue(plugin.readIsInHostSession())
    }

    @Test
    fun `an instance bound to an app session knows it does not run in the host session`() {
        val plugin = SessionKindProbePlugin().apply { bindSessionKind(isInHostSession = false) }

        assertFalse(plugin.readIsInHostSession())
    }

    @Test
    fun `isInHostSession before the host binds it says when it becomes available`() {
        val error = assertFailsWith<IllegalStateException> { SessionKindProbePlugin().readIsInHostSession() }

        assertContains(error.message.orEmpty(), "onCreate")
    }

    @Test
    fun `isInHostSession compiled into a plugin reads false on a host whose SDK predates it`() {
        val olderHostSdkClassLoader = OlderHostSdkClassLoader(compileJetWhaleHostPluginWithoutSessionKind())
        val probeClass = olderHostSdkClassLoader.loadClass(SessionKindProbePlugin::class.java.name)
        val probe = probeClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()

        val isInHostSession = probeClass.getDeclaredMethod("readIsInHostSession").apply { isAccessible = true }.invoke(probe)

        assertEquals(false, isInHostSession)
    }

    private class SessionKindProbePlugin : JetWhaleHostPlugin() {
        fun readIsInHostSession(): Boolean = isInHostSession
    }

    /**
     * Loads [SessionKindProbePlugin], as this SDK compiled it, against a [JetWhaleHostPlugin] from
     * before the session kind existed, the way a plugin built against this SDK runs in an older host.
     */
    private class OlderHostSdkClassLoader(private val olderJetWhaleHostPluginClassBytes: ByteArray) : ClassLoader(JetWhaleHostPluginTest::class.java.classLoader) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(getClassLoadingLock(name)) {
            findLoadedClass(name) ?: when (name) {
                JetWhaleHostPlugin::class.java.name -> defineClass(name, olderJetWhaleHostPluginClassBytes, 0, olderJetWhaleHostPluginClassBytes.size)

                SessionKindProbePlugin::class.java.name -> {
                    val probeClassBytes = checkNotNull(parent.getResourceAsStream(name.replace('.', '/') + ".class")).use { it.readBytes() }
                    defineClass(name, probeClassBytes, 0, probeClassBytes.size)
                }

                else -> super.loadClass(name, resolve)
            }
        }
    }

    private fun compileJetWhaleHostPluginWithoutSessionKind(): ByteArray {
        val directory = createTempDirectory()
        val source = directory.resolve("JetWhaleHostPlugin.java")
        source.writeText(
            """
            package com.kitakkun.jetwhale.host.sdk;

            public abstract class JetWhaleHostPlugin {
            }
            """.trimIndent(),
        )
        val exitCode = checkNotNull(ToolProvider.getSystemJavaCompiler()) { "The tests need a JDK to compile the older SDK class." }
            .run(null, null, null, "-d", directory.toString(), source.toString())
        check(exitCode == 0) { "Compiling the older JetWhaleHostPlugin failed." }
        return directory.resolve("com/kitakkun/jetwhale/host/sdk/JetWhaleHostPlugin.class").readBytes()
            .also { directory.toFile().deleteRecursively() }
    }
}
