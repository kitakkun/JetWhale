package com.kitakkun.jetwhale.debugger.host.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AnimatedSwappableContent(
    showContent1: Boolean,
    content1: @Composable () -> Unit,
    content2: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
    ) {
        AnimatedVisibility(
            visible = showContent1,
            enter = expandHorizontally(),
            exit = shrinkHorizontally(),
        ) {
            content1()
        }
        AnimatedVisibility(
            visible = !showContent1,
            enter = expandHorizontally(),
            exit = shrinkHorizontally(),
        ) {
            content2()
        }
    }
}