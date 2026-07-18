package com.kitakkun.jetwhale.host.settings

import com.kitakkun.jetwhale.host.architecture.PresenterContext
import com.kitakkun.jetwhale.host.architecture.ScreenContext
import com.kitakkun.jetwhale.host.model.ActivateSslCertificateMutationKey
import com.kitakkun.jetwhale.host.model.AdbAutoPortMappingMutationKey
import com.kitakkun.jetwhale.host.model.AppColorSchemeMutationKey
import com.kitakkun.jetwhale.host.model.AppLanguageMutationKey
import com.kitakkun.jetwhale.host.model.AppearanceSettingsSubscriptionKey
import com.kitakkun.jetwhale.host.model.CheckForUpdatesOnStartupMutationKey
import com.kitakkun.jetwhale.host.model.DeleteSslCertificateMutationKey
import com.kitakkun.jetwhale.host.model.DiagnosticsQueryKey
import com.kitakkun.jetwhale.host.model.FailedPluginJarPathsSubscriptionKey
import com.kitakkun.jetwhale.host.model.GenerateSslCertificateMutationKey
import com.kitakkun.jetwhale.host.model.HostVersionInfo
import com.kitakkun.jetwhale.host.model.LoadedPluginsMetaDataSubscriptionKey
import com.kitakkun.jetwhale.host.model.LogCaptureService
import com.kitakkun.jetwhale.host.model.McpServerPortMutationKey
import com.kitakkun.jetwhale.host.model.McpServerStatusSubscriptionKey
import com.kitakkun.jetwhale.host.model.OfficialPluginInstallMutationKey
import com.kitakkun.jetwhale.host.model.PluginInstallFromMavenMutationKey
import com.kitakkun.jetwhale.host.model.PluginInstallMutationKey
import com.kitakkun.jetwhale.host.model.PluginInstallProgressSubscriptionKey
import com.kitakkun.jetwhale.host.model.ServerPortMutationKey
import com.kitakkun.jetwhale.host.model.ServerStatusSubscriptionKey
import com.kitakkun.jetwhale.host.model.SettingsSubscriptionKey
import com.kitakkun.jetwhale.host.model.SslCertificatesSubscriptionKey
import com.kitakkun.jetwhale.host.model.TrustPluginMutationKey
import com.kitakkun.jetwhale.host.model.UntrustedPluginJarPathsSubscriptionKey
import com.kitakkun.jetwhale.host.model.UpdateCheckMutationKey
import com.kitakkun.jetwhale.host.model.UpdateInstallMutationKey
import dev.zacsweers.metro.Inject

/**
 * Presenter-role context shared by the settings sub-presenters. Settings is a single screen
 * with a segmented menu, so the presenter dependencies (mutations and the log capture
 * service) are aggregated here rather than per sub-presenter.
 */
@Inject
class SettingsPresenterContext(
    val appLanguageMutationKey: AppLanguageMutationKey,
    val appColorSchemeMutationKey: AppColorSchemeMutationKey,
    val adbAutoPortMappingMutationKey: AdbAutoPortMappingMutationKey,
    val serverPortMutationKey: ServerPortMutationKey,
    val mcpServerPortMutationKey: McpServerPortMutationKey,
    val pluginInstallMutationKey: PluginInstallMutationKey,
    val pluginInstallFromMavenMutationKey: PluginInstallFromMavenMutationKey,
    val trustPluginMutationKey: TrustPluginMutationKey,
    val officialPluginInstallMutationKey: OfficialPluginInstallMutationKey,
    val updateCheckMutationKey: UpdateCheckMutationKey,
    val updateInstallMutationKey: UpdateInstallMutationKey,
    val checkForUpdatesOnStartupMutationKey: CheckForUpdatesOnStartupMutationKey,
    val hostVersionInfo: HostVersionInfo,
    val generateSslCertificateMutationKey: GenerateSslCertificateMutationKey,
    val activateSslCertificateMutationKey: ActivateSslCertificateMutationKey,
    val deleteSslCertificateMutationKey: DeleteSslCertificateMutationKey,
    val logCaptureService: LogCaptureService,
) : PresenterContext

/**
 * Screen-role context: the subscription/query keys consumed by the sub-Roots, plus the
 * presenter context held by composition (has-a).
 */
@Inject
class SettingsScreenContext(
    val settingsSubscriptionKey: SettingsSubscriptionKey,
    val appearanceSettingsSubscriptionKey: AppearanceSettingsSubscriptionKey,
    val diagnosticsQueryKey: DiagnosticsQueryKey,
    val loadedPluginsMetaDataSubscriptionKey: LoadedPluginsMetaDataSubscriptionKey,
    val failedPluginJarPathsSubscriptionKey: FailedPluginJarPathsSubscriptionKey,
    val untrustedPluginJarPathsSubscriptionKey: UntrustedPluginJarPathsSubscriptionKey,
    val pluginInstallProgressSubscriptionKey: PluginInstallProgressSubscriptionKey,
    val serverStatusSubscriptionKey: ServerStatusSubscriptionKey,
    val mcpServerStatusSubscriptionKey: McpServerStatusSubscriptionKey,
    val sslCertificatesSubscriptionKey: SslCertificatesSubscriptionKey,
    val presenterContext: SettingsPresenterContext,
) : ScreenContext
