package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.AdditionalPluginDirectories
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.sdk.InternalJetWhaleHostApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest
import com.kitakkun.jetwhale.host.sdk.JetWhalePluginStorage
import com.kitakkun.jetwhale.host.sdk.get
import com.kitakkun.jetwhale.host.sdk.put
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(InternalJetWhaleHostApi::class)
class DefaultPluginStorageServiceTest {
    private val pluginId = "com.example.plugin"
    private lateinit var originalUserHome: String
    private lateinit var appDataDirectoryProvider: AppDataDirectoryProvider

    @BeforeTest
    fun setUp() {
        originalUserHome = System.getProperty("user.home")
        System.setProperty("user.home", Files.createTempDirectory("jetwhale-plugin-versions-test").toString())
        appDataDirectoryProvider = AppDataDirectoryProvider(AdditionalPluginDirectories(emptyList()))
    }

    @AfterTest
    fun restoreUserHome() {
        System.setProperty("user.home", originalUserHome)
    }

    @Test
    fun `an upgrade starts from a copy of the older version's data`() = runBlocking {
        val service = newService()
        service.storageFor(version("1.2.0")).put("filter", "errors-only")

        assertEquals("errors-only", newService().storageFor(version("1.3.0")).get<String>("filter"))
    }

    @Test
    fun `versions running side by side do not see each other's writes after the seed`() = runBlocking {
        val service = newService()
        val old = service.storageFor(version("1.2.0"))
        old.put("filter", "errors-only")
        val new = service.storageFor(version("1.3.0"))

        new.put("filter", "all")
        old.put("pinned", "row-1")

        assertEquals("errors-only", old.get<String>("filter"))
        assertEquals("all", new.get<String>("filter"))
        assertNull(new.get<String>("pinned"))
    }

    @Test
    fun `a version that has its own data is not seeded again on a later start`() = runBlocking {
        val repository = DefaultPluginDataStoreRepository(appDataDirectoryProvider)
        repository.seed(pluginId, "1.2.0", mapOf("filter" to JsonPrimitive("from 1.2.0")))
        repository.seed(pluginId, "1.3.0", mapOf("filter" to JsonPrimitive("from 1.3.0")))

        assertEquals("from 1.3.0", DefaultPluginStorageService(repository).storageFor(version("1.3.0")).get<String>("filter"))
    }

    @Test
    fun `the seed comes from the nearest older version`() = runBlocking {
        val service = newService()
        service.storageFor(version("1.2.0")).put("filter", "from 1.2.0")
        service.storageFor(version("1.9.0")).put("filter", "from 1.9.0")
        service.storageFor(version("1.11.0")).put("filter", "from 1.11.0")

        assertEquals("from 1.9.0", service.storageFor(version("1.10.0")).get<String>("filter"))
    }

    @Test
    fun `a version older than every stored one starts empty`() = runBlocking {
        val service = newService()
        service.storageFor(version("1.3.0")).put("filter", "from 1.3.0")

        assertNull(service.storageFor(version("1.2.0")).get<String>("filter"))
    }

    @Test
    fun `data stored before versions were kept apart seeds the first version`() = runBlocking {
        val unversionedStore = File(appDataDirectoryProvider.resolvePluginDataDir(pluginId).toString(), "store.json")
        unversionedStore.parentFile.mkdirs()
        unversionedStore.writeText("""{"filter":"errors-only"}""")

        assertEquals("errors-only", newService().storageFor(version("1.2.0")).get<String>("filter"))
    }

    @Test
    fun `a version with a newer storage format migrates its copy and leaves the older version's alone`() = runBlocking {
        val service = newService()
        val old = service.storageFor(version("1.2.0"))
        // Bound as a plugin would be, so the copy carries the storage version stamp.
        StoringPlugin(storageVersion = 1, migrate = {}).apply { bindStorage(old) }.write("draft", "hello")

        val newPlugin = StoringPlugin(storageVersion = 2, migrate = { storage ->
            storage.get<String>("draft")?.let { storage.put("draft-input", it) }
            storage.remove("draft")
        })
        newPlugin.bindStorage(service.storageFor(version("1.3.0")))

        assertEquals("hello", newPlugin.read("draft-input"))
        assertNull(newPlugin.read("draft"))
        assertEquals("hello", old.get<String>("draft"))
    }

    private fun newService() = DefaultPluginStorageService(DefaultPluginDataStoreRepository(appDataDirectoryProvider))

    private fun version(version: String) = LoadedHostPlugin(
        manifest = JetWhaleHostPluginManifest(
            pluginId = pluginId,
            pluginName = "Example",
            version = version,
            factoryClass = "com.example.Factory",
        ),
        factory = object : JetWhaleHostPluginFactory {
            override fun createPlugin(): JetWhaleHostPlugin = error("not created in this test")
        },
        jarPath = "/plugins/example-$version.jar",
    )

    private class StoringPlugin(
        override val storageVersion: Int,
        private val migrate: suspend (JetWhalePluginStorage) -> Unit,
    ) : JetWhaleHostPlugin() {
        override suspend fun onStorageMigrate(fromVersion: Int) = migrate(storage)

        suspend fun write(key: String, value: String) = storage.put(key, value)

        suspend fun read(key: String): String? = storage.get<String>(key)
    }
}
