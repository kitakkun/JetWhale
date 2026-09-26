package com.kitakkun.jetwhale.host.drawer

import com.kitakkun.jetwhale.host.architecture.PresenterContext
import com.kitakkun.jetwhale.host.architecture.ScreenContext
import com.kitakkun.jetwhale.host.model.BoundPluginVersionsSubscriptionKey
import com.kitakkun.jetwhale.host.model.DebugSessionsSubscriptionKey
import com.kitakkun.jetwhale.host.model.EnabledPluginsSubscriptionKey
import com.kitakkun.jetwhale.host.model.FailedPluginJarPathsSubscriptionKey
import com.kitakkun.jetwhale.host.model.FollowAiOperationMutationKey
import com.kitakkun.jetwhale.host.model.HeadlessPluginsSubscriptionKey
import com.kitakkun.jetwhale.host.model.HostNavigationService
import com.kitakkun.jetwhale.host.model.LoadedPluginsMetaDataSubscriptionKey
import com.kitakkun.jetwhale.host.model.McpActivitySubscriptionKey
import com.kitakkun.jetwhale.host.model.McpCapablePluginsSubscriptionKey
import com.kitakkun.jetwhale.host.model.SaveSidebarWidthMutationKey
import com.kitakkun.jetwhale.host.model.SetPluginEnabledMutationKey
import com.kitakkun.jetwhale.host.model.SettingsSubscriptionKey
import com.kitakkun.jetwhale.host.model.SidebarWidthSubscriptionKey
import dev.zacsweers.metro.Inject

/**
 * Presenter-role context: only the dependencies the presenter consumes.
 */
@Inject
class ToolingScaffoldPresenterContext(
    val setPluginEnabledMutationKey: SetPluginEnabledMutationKey,
    val followAiOperationMutationKey: FollowAiOperationMutationKey,
    val saveSidebarWidthMutationKey: SaveSidebarWidthMutationKey,
) : PresenterContext

/**
 * Screen-role context: the dependencies the Root consumes, plus the presenter context held
 * by composition (has-a) so the presenter can be supplied a right-sized [PresenterContext].
 */
@Inject
class ToolingScaffoldScreenContext(
    val loadedPluginsMetaDataSubscriptionKey: LoadedPluginsMetaDataSubscriptionKey,
    val debugSessionsSubscriptionKey: DebugSessionsSubscriptionKey,
    val enabledPluginsSubscriptionKey: EnabledPluginsSubscriptionKey,
    val failedPluginJarPathsSubscriptionKey: FailedPluginJarPathsSubscriptionKey,
    val mcpActivitySubscriptionKey: McpActivitySubscriptionKey,
    val mcpCapablePluginsSubscriptionKey: McpCapablePluginsSubscriptionKey,
    val settingsSubscriptionKey: SettingsSubscriptionKey,
    val headlessPluginsSubscriptionKey: HeadlessPluginsSubscriptionKey,
    val boundPluginVersionsSubscriptionKey: BoundPluginVersionsSubscriptionKey,
    val sidebarWidthSubscriptionKey: SidebarWidthSubscriptionKey,
    val hostNavigationService: HostNavigationService,
    val presenterContext: ToolingScaffoldPresenterContext,
) : ScreenContext
