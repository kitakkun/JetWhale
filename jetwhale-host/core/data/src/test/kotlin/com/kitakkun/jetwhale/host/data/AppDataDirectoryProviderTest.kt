package com.kitakkun.jetwhale.host.data

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppDataDirectoryProviderTest {
    private val originalUserHome: String? = System.getProperty("user.home")
    private val originalAppDataDir: String? = System.getProperty(AppDataDirectoryProvider.APP_DATA_DIR_PROPERTY)

    @AfterTest
    fun restoreProperties() {
        originalUserHome?.let { System.setProperty("user.home", it) }
        if (originalAppDataDir != null) {
            System.setProperty(AppDataDirectoryProvider.APP_DATA_DIR_PROPERTY, originalAppDataDir)
        } else {
            System.clearProperty(AppDataDirectoryProvider.APP_DATA_DIR_PROPERTY)
        }
    }

    @Test
    fun `defaults every path under the user home when no override is set`() {
        val home = Files.createTempDirectory("jetwhale-home").toString()
        System.setProperty("user.home", home)
        System.clearProperty(AppDataDirectoryProvider.APP_DATA_DIR_PROPERTY)

        val provider = AppDataDirectoryProvider()

        assertEquals("~/.jetwhale", provider.getAppDataPath())
        assertTrue(provider.getPluginDirectory().path.startsWith("$home/.jetwhale"))
        assertTrue(provider.getTrustRegistryFile().path.startsWith("$home/.jetwhale"))
        assertTrue(
            provider.resolveDataStoreFilePath("prefs.preferences_pb").toString().startsWith("$home/.jetwhale"),
        )
    }

    @Test
    fun `routes every path through the sandbox root when the override is set`() {
        val home = Files.createTempDirectory("jetwhale-home").toString()
        val sandbox = Files.createTempDirectory("jetwhale-sandbox").toString()
        System.setProperty("user.home", home)
        System.setProperty(AppDataDirectoryProvider.APP_DATA_DIR_PROPERTY, sandbox)

        val provider = AppDataDirectoryProvider()

        // Nothing resolves under the real home; everything flows from the sandbox root.
        assertEquals(sandbox, provider.getAppDataPath())
        assertTrue(provider.getPluginDirectory().path.startsWith(sandbox))
        assertTrue(provider.getPluginLibsDirectory().path.startsWith(sandbox))
        assertTrue(provider.getTrustRegistryFile().path.startsWith(sandbox))
        assertTrue(provider.resolveDataStoreFilePath("prefs.preferences_pb").toString().startsWith(sandbox))
        assertTrue(provider.resolvePluginDataFilePath("plugin.a").toString().startsWith(sandbox))

        provider.createAppDataDirectoriesIfNeeded()
        assertTrue(provider.getPluginDirectory().exists())
    }

    @Test
    fun `treats a blank override as no override`() {
        val home = Files.createTempDirectory("jetwhale-home").toString()
        System.setProperty("user.home", home)
        System.setProperty(AppDataDirectoryProvider.APP_DATA_DIR_PROPERTY, "   ")

        val provider = AppDataDirectoryProvider()

        assertEquals("~/.jetwhale", provider.getAppDataPath())
        assertTrue(provider.getPluginDirectory().path.startsWith("$home/.jetwhale"))
    }
}
