package com.kitakkun.jetwhale.host.drawer

import com.kitakkun.jetwhale.host.architecture.PresenterContext
import com.kitakkun.jetwhale.host.architecture.ScreenContext
import com.kitakkun.jetwhale.host.model.DebugSessionsSubscriptionKey
import com.kitakkun.jetwhale.host.model.EnabledPluginsSubscriptionKey
import com.kitakkun.jetwhale.host.model.FailedPluginJarPathsSubscriptionKey
import com.kitakkun.jetwhale.host.model.LoadedPluginsMetaDataSubscriptionKey
import com.kitakkun.jetwhale.host.model.SetPluginEnabledMutationKey
import dev.zacsweers.metro.Inject

/**
 * Presenter-role context: only the dependencies the presenter consumes.
 */
@Inject
class ToolingScaffoldPresenterContext(
    val setPluginEnabledMutationKey: SetPluginEnabledMutationKey,
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
    val presenterContext: ToolingScaffoldPresenterContext,
) : ScreenContext
