package com.kitakkun.jetwhale.host.settings.plugin

import com.kitakkun.jetwhale.host.model.InstalledPluginVersion
import com.kitakkun.jetwhale.host.settings.component.PluginInfoUiState
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginSettingsScreenUiStateTest {
    @Test
    fun `a jar lists every plugin version it provides and nothing from other jars`() {
        val uiState = uiStateOf(
            PluginInfoUiState(
                id = "com.example.network",
                name = "Network",
                installedVersions = persistentListOf(
                    InstalledPluginVersion(version = "2.0.0", jarPath = "/plugins/network-2.jar", removable = true),
                    InstalledPluginVersion(version = "1.0.0", jarPath = "/plugins/bundle.jar", removable = true),
                ),
            ),
            PluginInfoUiState(
                id = "com.example.storage",
                name = "Storage",
                installedVersions = persistentListOf(
                    InstalledPluginVersion(version = "1.0.0", jarPath = "/plugins/bundle.jar", removable = true),
                ),
            ),
        )

        assertEquals(listOf("Network" to "1.0.0", "Storage" to "1.0.0"), uiState.versionsInJar("/plugins/bundle.jar"))
        assertEquals(listOf("Network" to "2.0.0"), uiState.versionsInJar("/plugins/network-2.jar"))
    }

    private fun uiStateOf(vararg plugins: PluginInfoUiState) = PluginSettingsScreenUiState(
        plugins = persistentListOf(*plugins),
        officialPlugins = persistentListOf(),
        failedJars = persistentListOf(),
        untrustedJarPaths = persistentListOf(),
        signPluginTrustRegistry = false,
        installJobs = persistentListOf(),
        isAddingFromFile = false,
        addFromFileError = null,
    )
}
