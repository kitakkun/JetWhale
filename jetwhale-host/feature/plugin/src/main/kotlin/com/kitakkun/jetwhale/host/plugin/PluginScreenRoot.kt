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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.host.architecture.SoilFallbackDefaults
import kotlinx.coroutines.delay
import soil.query.compose.rememberQuery

@OptIn(InternalComposeUiApi::class)
@Composable
context(screenContext: PluginScreenContext)
fun PluginScreenRoot() {
    var reset by remember { mutableStateOf(false) }
    // Bumped on every hot reload to trigger the transient "Hot reloaded" indicator.
    var reloadCount by remember { mutableIntStateOf(0) }

    // On hot reload, toggling `reset` re-creates the query so a fresh compose scene is built from the
    // freshly loaded plugin code. Inert in production (the flow never emits without a dev directory).
    LaunchedEffect(screenContext) {
        screenContext.pluginReloadedFlow.collect {
            reset = !reset
            reloadCount++
        }
    }

    Box(Modifier.fillMaxSize()) {
        key(reset) {
            SoilDataBoundary(
                state = rememberQuery(screenContext.pluginComposeSceneQueryKey),
                fallback = SoilFallbackDefaults.custom(
                    suspenseFallback = SoilFallbackDefaults.default().suspenseFallback,
                    errorFallback = {
                        PluginScreenErrorFallback(
                            pluginId = screenContext.pluginId,
                            errorBoundaryContext = it,
                            // force recompose when reset is clicked
                            onClickReset = { reset = !reset },
                        )
                    },
                ),
            ) { pluginComposeScene ->
                PluginScreen(pluginComposeScene = pluginComposeScene)
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

/**
 * Shows a brief "Hot reloaded" badge each time [reloadCount] changes (i.e. on every hot reload),
 * fading out shortly after so it does not obscure the plugin UI. Never appears in production, where
 * hot reload is inert.
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
        Surface(
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(50),
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("⟳", style = MaterialTheme.typography.labelMedium)
                Text("Hot reloaded", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
