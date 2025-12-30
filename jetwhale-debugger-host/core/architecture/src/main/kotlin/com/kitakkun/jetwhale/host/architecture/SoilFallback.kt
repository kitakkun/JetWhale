package com.kitakkun.jetwhale.host.architecture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                CircularWavyProgressIndicator()
            }
        }
        override val errorFallback: @Composable (ErrorBoundaryContext) -> Unit = {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Error: ${it.err.localizedMessage}",
                )
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
    ): SoilFallback {
        return CustomFallback(
            suspenseFallback = suspenseFallback,
            errorFallback = errorFallback,
        )
    }
}

