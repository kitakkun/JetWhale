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
import com.kitakkun.jetwhale.host.architecture.MutationErrorEffect
import com.kitakkun.jetwhale.host.architecture.PresenterContext
import com.kitakkun.jetwhale.host.architecture.ScreenChannel
import com.kitakkun.jetwhale.host.architecture.ScreenContext
import com.kitakkun.jetwhale.host.architecture.rememberScreenChannel
import com.kitakkun.jetwhale.host.enable
import com.kitakkun.jetwhale.host.model.SetPluginEnabledMutationKey
import com.kitakkun.jetwhale.host.model.SetPluginEnabledParams
import com.kitakkun.jetwhale.host.navigation.DisabledPluginNavKey
import com.kitakkun.jetwhale.host.plugin_disabled_title
import com.kitakkun.jetwhale.host.plugin_enable_failed
import com.kitakkun.jetwhale.host.plugin_not_in_app
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import dev.zacsweers.metro.Inject
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
 * switched on, and opens once it is ([onEnabled]); one the selected app doesn't include only says so.
 */
@Composable
context(screenContext: DisabledPluginScreenContext)
fun DisabledPluginScreenRoot(
    navKey: DisabledPluginNavKey,
    onEnabled: () -> Unit,
) {
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
        notInApp = navKey.notInApp,
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
                setPluginEnabledMutation.mutateAsync(SetPluginEnabledParams(pluginId, enabled = true))
                screenChannel.emit(DisabledPluginScreenActionResult.Enabled)
            }
        }
    }
    MutationErrorEffect(setPluginEnabledMutation) {
        screenChannel.emit(DisabledPluginScreenActionResult.EnableFailed)
    }
}

@Composable
fun DisabledPluginScreen(
    pluginName: String,
    notInApp: Boolean,
    enableFailed: Boolean,
    onClickEnable: () -> Unit,
) {
    if (notInApp) {
        JwEmptyState(title = pluginName, description = stringResource(Res.string.plugin_not_in_app))
        return
    }
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
    DisabledPluginScreen(pluginName = "Network Inspector", notInApp = false, enableFailed = false, onClickEnable = {})
}

@Preview
@Composable
private fun NotInAppPluginScreenPreview() {
    DisabledPluginScreen(pluginName = "Network Inspector", notInApp = true, enableFailed = false, onClickEnable = {})
}
