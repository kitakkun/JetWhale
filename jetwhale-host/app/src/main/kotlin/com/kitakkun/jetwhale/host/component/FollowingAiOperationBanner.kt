package com.kitakkun.jetwhale.host.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.following_ai_operation
import com.kitakkun.jetwhale.host.stop_following_ai_operation
import com.kitakkun.jetwhale.host.ui.JwBanner
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwTone
import org.jetbrains.compose.resources.stringResource

/**
 * Says that the window moved on its own, and offers the switch that stops it.
 *
 * It sits above the content rather than over it — a following window is showing a plugin the user
 * wants to watch, and a floating snackbar would cover exactly the thing it is announcing. Expanding
 * the banner pushes the plugin down instead, so nothing is hidden.
 */
@Composable
fun FollowingAiOperationBanner(
    visible: Boolean,
    toolName: String,
    onClickStopFollowing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        JwBanner(
            // The tool name is what the agent is doing right now; the plugin it targets is already
            // on screen, so naming it here would only repeat what the user sees.
            text = stringResource(Res.string.following_ai_operation, toolName),
            tone = JwTone.Warning,
            icon = { JwIcon(imageVector = Icons.Default.SmartToy, contentDescription = null) },
            actions = {
                JwButton(
                    text = stringResource(Res.string.stop_following_ai_operation),
                    onClick = onClickStopFollowing,
                    style = JwButtonStyle.Text,
                )
            },
        )
    }
}

@Preview
@Composable
private fun FollowingAiOperationBannerPreview() {
    FollowingAiOperationBanner(
        visible = true,
        toolName = "jetwhale.click",
        onClickStopFollowing = {},
    )
}
