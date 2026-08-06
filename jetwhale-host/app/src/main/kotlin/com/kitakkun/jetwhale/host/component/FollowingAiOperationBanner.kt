package com.kitakkun.jetwhale.host.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.following_ai_operation
import com.kitakkun.jetwhale.host.stop_following_ai_operation
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
        Surface(color = MaterialTheme.colorScheme.primaryContainer) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    // The tool name is what the agent is doing right now; the plugin it targets is
                    // already on screen, so naming it here would only repeat what the user sees.
                    text = stringResource(Res.string.following_ai_operation, toolName),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClickStopFollowing) {
                    Text(stringResource(Res.string.stop_following_ai_operation))
                }
            }
        }
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
