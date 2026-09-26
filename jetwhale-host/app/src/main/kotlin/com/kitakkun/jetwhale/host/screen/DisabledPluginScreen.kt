package com.kitakkun.jetwhale.host.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.architecture.ActionEffect
import com.kitakkun.jetwhale.host.architecture.ActionResultEffect
import com.kitakkun.jetwhale.host.architecture.PresenterContext
import com.kitakkun.jetwhale.host.architecture.ScreenChannel
import com.kitakkun.jetwhale.host.architecture.ScreenContext
import com.kitakkun.jetwhale.host.architecture.rememberScreenChannel
import com.kitakkun.jetwhale.host.enable
import com.kitakkun.jetwhale.host.model.HostVersionInfo
import com.kitakkun.jetwhale.host.model.SetPluginEnabledMutationKey
import com.kitakkun.jetwhale.host.model.SetPluginEnabledParams
import com.kitakkun.jetwhale.host.navigation.DisabledPluginNavKey
import com.kitakkun.jetwhale.host.plugin_disabled_title
import com.kitakkun.jetwhale.host.plugin_enable_failed
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwTheme
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.stringResource
import soil.query.compose.rememberMutation

/** Presenter-role context: only the dependencies the presenter consumes. */
@Inject
class DisabledPluginPresenterContext(
    val setPluginEnabledMutationKey: SetPluginEnabledMutationKey,
) : PresenterContext

/** Screen-role context, holding the presenter's. */
@Inject
class DisabledPluginScreenContext(
    val hostVersionInfo: HostVersionInfo,
    val presenterContext: DisabledPluginPresenterContext,
) : ScreenContext

sealed interface DisabledPluginScreenAction {
    data object Enable : DisabledPluginScreenAction
}

sealed interface DisabledPluginScreenActionResult {
    data object Enabled : DisabledPluginScreenActionResult
    data object EnableFailed : DisabledPluginScreenActionResult
}

/**
 * The content pane for a plugin the drawer lists greyed out: a switched-off plugin offers to be
 * switched on, and opens once it is ([onEnabled]); one the selected app doesn't include says how to
 * add it to the app.
 */
@Composable
context(screenContext: DisabledPluginScreenContext)
fun DisabledPluginScreenRoot(
    navKey: DisabledPluginNavKey,
    onEnabled: () -> Unit,
) {
    if (navKey.notInApp) {
        NotInAppPluginScreen(pluginName = navKey.pluginName, setup = AgentSetup.forPlugin(navKey.pluginId, screenContext.hostVersionInfo))
        return
    }
    val screenChannel = rememberScreenChannel<DisabledPluginScreenAction, DisabledPluginScreenActionResult>()
    var enableFailed by remember { mutableStateOf(false) }
    ActionResultEffect(screenChannel) { result ->
        when (result) {
            is DisabledPluginScreenActionResult.Enabled -> onEnabled()
            is DisabledPluginScreenActionResult.EnableFailed -> enableFailed = true
        }
    }
    context(screenContext.presenterContext) {
        DisabledPluginPresenter(screenChannel = screenChannel, pluginId = navKey.pluginId)
    }
    DisabledPluginScreen(
        pluginName = navKey.pluginName,
        enableFailed = enableFailed,
        onClickEnable = {
            enableFailed = false
            screenChannel.send(DisabledPluginScreenAction.Enable)
        },
    )
}

@Composable
context(presenterContext: DisabledPluginPresenterContext)
private fun DisabledPluginPresenter(
    screenChannel: ScreenChannel<DisabledPluginScreenAction, DisabledPluginScreenActionResult>,
    pluginId: String,
) {
    val setPluginEnabledMutation = rememberMutation(presenterContext.setPluginEnabledMutationKey)
    ActionEffect(screenChannel) { action ->
        when (action) {
            is DisabledPluginScreenAction.Enable -> {
                // mutate, not mutateAsync: it waits until the setting is stored, so the plugin opens
                // only once it is on, and a failure is reported here instead of after navigating away.
                // Whatever keeps the setting from being stored is shown as "could not enable"; the
                // screen must not crash over it.
                @Suppress("KOTRAIL_CATCH_TOO_BROAD")
                val result = try {
                    setPluginEnabledMutation.mutate(SetPluginEnabledParams(pluginId, enabled = true))
                    DisabledPluginScreenActionResult.Enabled
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    DisabledPluginScreenActionResult.EnableFailed
                }
                screenChannel.emit(result)
            }
        }
    }
}

@Composable
fun DisabledPluginScreen(
    pluginName: String,
    enableFailed: Boolean,
    onClickEnable: () -> Unit,
) {
    JwEmptyState(
        title = stringResource(Res.string.plugin_disabled_title, pluginName),
        description = if (enableFailed) stringResource(Res.string.plugin_enable_failed) else null,
        action = {
            JwButton(text = stringResource(Res.string.enable), onClick = onClickEnable, style = JwButtonStyle.Primary)
        },
    )
}

@Preview
@Composable
private fun DisabledPluginScreenPreview() {
    JwTheme(darkTheme = false) {
        DisabledPluginScreen(pluginName = "Network Inspector", enableFailed = false, onClickEnable = {})
    }
}
