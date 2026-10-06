package com.kitakkun.jetwhale.host.plugin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.host.architecture.SoilFallbackDefaults
import com.kitakkun.jetwhale.host.model.PluginScreenState
import com.kitakkun.jetwhale.host.ui.JwSurface
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import soil.plant.compose.reacty.ErrorBoundary
import soil.query.compose.rememberSubscription

@OptIn(InternalComposeUiApi::class)
@Composable
context(screenContext: PluginScreenContext)
fun PluginScreenRoot() {
    var reloadCount by remember { mutableIntStateOf(0) }
    var crashReloadCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(screenContext) {
        screenContext.pluginReloadedFlow.collect { reloadCount++ }
    }

    Box(Modifier.fillMaxSize()) {
        val screenState = rememberSubscription(screenContext.pluginScreenStateSubscriptionKey)
        val coroutineScope = rememberCoroutineScope()
        SoilDataBoundary(
            state = screenState,
            fallback = SoilFallbackDefaults.custom(
                suspenseFallback = { PluginStartingScreen() },
                errorFallback = SoilFallbackDefaults.default().errorFallback,
            ),
        ) { state ->
            when (state) {
                PluginScreenState.Starting -> PluginStartingScreen()

                // soil's ErrorBoundary keeps an error that PluginScreen hands it from the plugin's
                // input until the boundary is recreated, so each scene and each Reload get a fresh
                // boundary.
                is PluginScreenState.Ready -> key(state.scene, crashReloadCount) {
                    ErrorBoundary(
                        fallback = {
                            PluginScreenErrorFallback(
                                title = stringResource(Res.string.plugin_ui_crash_title),
                                hint = null,
                                pluginId = screenContext.pluginId,
                                cause = it.err,
                                onClickReset = { crashReloadCount++ },
                            )
                        },
                    ) {
                        PluginScreen(pluginComposeScene = state.scene)
                    }
                }

                PluginScreenState.Headless -> HeadlessPluginScreen(pluginId = screenContext.pluginId)

                is PluginScreenState.FailedToStart -> PluginScreenErrorFallback(
                    title = stringResource(Res.string.plugin_start_failed_title),
                    hint = stringResource(Res.string.plugin_start_failed_hint),
                    pluginId = screenContext.pluginId,
                    cause = state.cause,
                    onClickReset = null,
                )

                is PluginScreenState.ContentFailed -> PluginScreenErrorFallback(
                    title = stringResource(Res.string.plugin_ui_crash_title),
                    hint = null,
                    pluginId = screenContext.pluginId,
                    cause = state.cause,
                    onClickReset = { coroutineScope.launch { screenState.reset() } },
                )
            }
        }

        HotReloadIndicator(
            reloadCount = reloadCount,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        )
    }
}

/** Lift of the hot-reload badge over the plugin content. */
private val HOT_RELOAD_BADGE_SHADOW = 4.dp

/**
 * Shows a brief "Reloaded" badge each time [reloadCount] changes (i.e. on every reload), fading
 * out shortly after so it does not obscure the plugin UI.
 */
@Composable
private fun HotReloadIndicator(reloadCount: Int, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(reloadCount) {
        if (reloadCount == 0) return@LaunchedEffect
        visible = true
        delay(1_500)
        visible = false
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        val shape = RoundedCornerShape(percent = 50)
        JwSurface(
            color = JwTheme.colors.accent,
            contentColor = JwTheme.colors.onAccent,
            shape = shape,
            modifier = Modifier.shadow(HOT_RELOAD_BADGE_SHADOW, shape),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                JwText("⟳", style = JwTheme.textStyles.label)
                JwText("Reloaded", style = JwTheme.textStyles.label)
            }
        }
    }
}
