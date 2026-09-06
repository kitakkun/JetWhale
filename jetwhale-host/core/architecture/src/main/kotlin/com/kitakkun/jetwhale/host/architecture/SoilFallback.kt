package com.kitakkun.jetwhale.host.architecture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.ui.JwProgressIndicator
import com.kitakkun.jetwhale.host.ui.JwProgressIndicatorDefaults
import com.kitakkun.jetwhale.host.ui.JwText
import soil.plant.compose.reacty.ErrorBoundaryContext

sealed interface SoilFallback {
    val suspenseFallback: @Composable () -> Unit
    val errorFallback: @Composable (ErrorBoundaryContext) -> Unit
}

object SoilFallbackDefaults {
    private object EmptySoilFallback : SoilFallback {
        override val suspenseFallback: @Composable () -> Unit = {}
        override val errorFallback: @Composable (ErrorBoundaryContext) -> Unit = {}
    }

    private object DefaultFallback : SoilFallback {
        override val suspenseFallback: @Composable () -> Unit = {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                JwProgressIndicator(size = JwProgressIndicatorDefaults.largeSize)
            }
        }
        override val errorFallback: @Composable (ErrorBoundaryContext) -> Unit = {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                JwText(text = "Error: ${it.err.localizedMessage}")
            }
        }
    }

    private data class CustomFallback(
        override val suspenseFallback: @Composable () -> Unit,
        override val errorFallback: @Composable (ErrorBoundaryContext) -> Unit,
    ) : SoilFallback

    fun default(): SoilFallback = DefaultFallback
    fun none(): SoilFallback = EmptySoilFallback
    fun custom(
        suspenseFallback: @Composable () -> Unit,
        errorFallback: @Composable (ErrorBoundaryContext) -> Unit,
    ): SoilFallback = CustomFallback(
        suspenseFallback = suspenseFallback,
        errorFallback = errorFallback,
    )
}
