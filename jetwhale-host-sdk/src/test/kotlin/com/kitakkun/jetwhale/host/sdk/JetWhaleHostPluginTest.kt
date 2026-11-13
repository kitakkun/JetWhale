package com.kitakkun.jetwhale.host.sdk

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicInteger
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

    @Test
    fun `instances that share a store run its migration once between them`() = runBlocking {
        val store = InMemoryPluginStorage()
        store.put("draft", "unversioned", String.serializer())
        val migrationsStarted = AtomicInteger()
        val migrationMayFinish = CompletableDeferred<Unit>()
        val instances = List(2) {
            MigratingPlugin(onMigrate = {
                migrationsStarted.incrementAndGet()
                migrationMayFinish.await()
            }).apply { bindStorage(store) }
        }

        val reads = instances.map { async(start = CoroutineStart.UNDISPATCHED) { it.readDraft() } }
        val migrationsStartedWhileTheFirstRuns = migrationsStarted.get()
        migrationMayFinish.complete(Unit)

        assertEquals(listOf<String?>("unversioned", "unversioned"), reads.awaitAll())
        assertEquals(1, migrationsStartedWhileTheFirstRuns)
        assertEquals(1, migrationsStarted.get())
    }

    private class SessionKindProbePlugin : JetWhaleHostPlugin() {
        fun readIsInHostSession(): Boolean = isInHostSession
    }

    private class MigratingPlugin(private val onMigrate: suspend () -> Unit) : JetWhaleHostPlugin() {
        override val storageVersion: Int = 2

        override suspend fun onStorageMigrate(fromVersion: Int) {
            onMigrate()
        }

        suspend fun readDraft(): String? = storage.get("draft", String.serializer())
    }

    private class InMemoryPluginStorage : JetWhalePluginStorage {
        private val encodedValues = MutableStateFlow(emptyMap<String, String>())

        override suspend fun <T> put(key: String, value: T, serializer: KSerializer<T>) {
            encodedValues.update { it + (key to Json.encodeToString(serializer, value)) }
        }

        override suspend fun <T> get(key: String, serializer: KSerializer<T>): T? = encodedValues.value[key]?.let { Json.decodeFromString(serializer, it) }

        override fun <T> getFlow(key: String, serializer: KSerializer<T>): Flow<T?> = encodedValues.map { values -> values[key]?.let { Json.decodeFromString(serializer, it) } }

        override suspend fun contains(key: String): Boolean = key in encodedValues.value

        override suspend fun remove(key: String) {
            encodedValues.update { it - key }
        }

        override suspend fun clear() {
            encodedValues.value = emptyMap()
        }

        override val keysFlow: Flow<Set<String>> = encodedValues.map { it.keys }
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
