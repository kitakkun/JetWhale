package com.kitakkun.jetwhale.host.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.BuildConfig
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.app_icon
import com.kitakkun.jetwhale.host.app_name
import com.kitakkun.jetwhale.host.developed_by
import com.kitakkun.jetwhale.host.github_url
import com.kitakkun.jetwhale.host.oss_licenses
import com.kitakkun.jetwhale.host.ui.JwHorizontalDivider
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwListItem
import com.kitakkun.jetwhale.host.ui.JwPanel
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.version_format
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Wide enough for the repository URL on one line. */
private val PanelWidth = 380.dp

private val AppIconSize = 72.dp

/** Shrinks the chevron glyph to a secondary size. */
private val ChevronInset = 3.dp

@Composable
fun InfoScreen(
    onClickOSSLicenses: () -> Unit,
) {
    JwPanel(modifier = Modifier.width(PanelWidth)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = JwSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(Res.drawable.app_icon),
                contentDescription = null,
                modifier = Modifier
                    .size(AppIconSize)
                    .padding(bottom = JwSpacing.medium),
            )
            JwText(
                text = stringResource(Res.string.app_name),
                style = JwTheme.textStyles.title,
            )
            JwText(
                text = stringResource(Res.string.version_format, BuildConfig.VERSION),
                style = JwTheme.textStyles.bodySmall,
                color = JwTheme.colors.textSecondary,
            )
            JwText(
                text = stringResource(Res.string.developed_by),
                style = JwTheme.textStyles.bodySmall,
                color = JwTheme.colors.textSecondary,
            )
            JwText(
                text = stringResource(Res.string.github_url),
                style = JwTheme.textStyles.bodySmall,
                color = JwTheme.colors.textSecondary,
            )
        }
        JwHorizontalDivider()
        JwListItem(
            text = stringResource(Res.string.oss_licenses),
            selected = false,
            onClick = onClickOSSLicenses,
            trailingContent = {
                JwIcon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = JwTheme.colors.textSecondary,
                    modifier = Modifier.padding(ChevronInset),
                )
            },
        )
    }
}
