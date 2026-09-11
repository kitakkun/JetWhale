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
import com.kitakkun.jetwhale.host.follow_ai_operation_armed
import com.kitakkun.jetwhale.host.following_ai_operation
import com.kitakkun.jetwhale.host.stop_following_ai_operation
import com.kitakkun.jetwhale.host.ui.JwBanner
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwTone
import org.jetbrains.compose.resources.stringResource

/**
 * Says that the window moves on its own while an agent operates, and offers the switch that stops it.
 *
 * It sits above the content rather than over it — a following window is showing a plugin the user
 * wants to watch, and a floating snackbar would cover exactly the thing it is announcing.
 *
 * The strip stays up for the whole time a follow could happen, not just during one: while it is
 * [visible] only its tone and text change as calls come and go, so the plugin below keeps its place
 * through a burst of operations. Expanding and collapsing — the one thing that moves the plugin —
 * happens only when an agent connects or leaves, or the mode is switched.
 *
 * @param followingToolName the tool whose call is moving the window right now, or `null` while the
 * strip is only standing by.
 */
@Composable
fun FollowingAiOperationBanner(
    visible: Boolean,
    followingToolName: String?,
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
            text = followingToolName
                ?.let { stringResource(Res.string.following_ai_operation, it) }
                ?: stringResource(Res.string.follow_ai_operation_armed),
            tone = if (followingToolName != null) JwTone.Warning else JwTone.Info,
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
private fun FollowingAiOperationBannerArmedPreview() {
    FollowingAiOperationBanner(
        visible = true,
        followingToolName = null,
        onClickStopFollowing = {},
    )
}

@Preview
@Composable
private fun FollowingAiOperationBannerFollowingPreview() {
    FollowingAiOperationBanner(
        visible = true,
        followingToolName = "jetwhale.click",
        onClickStopFollowing = {},
    )
}
