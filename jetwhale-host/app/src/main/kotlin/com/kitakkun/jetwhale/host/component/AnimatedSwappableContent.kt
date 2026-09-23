package com.kitakkun.jetwhale.host.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.ui.JwText

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

@Preview
@Composable
private fun AnimatedSwappableContentPreview() {
    AnimatedSwappableContent(
        showContent1 = true,
        content1 = { JwText(text = "First") },
        content2 = { JwText(text = "Second") },
    )
}
