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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.version_format
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun InfoScreen(
    onClickOSSLicenses: () -> Unit,
) {
    JwPanel(modifier = Modifier.width(380.dp)) {
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
                    .size(72.dp)
                    .padding(bottom = JwSpacing.medium),
            )
            Text(
                text = stringResource(Res.string.app_name),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(Res.string.version_format, BuildConfig.VERSION),
                style = MaterialTheme.typography.bodySmall,
                color = JwTheme.colors.textSecondary,
            )
            Text(
                text = stringResource(Res.string.developed_by),
                style = MaterialTheme.typography.bodySmall,
                color = JwTheme.colors.textSecondary,
            )
            Text(
                text = stringResource(Res.string.github_url),
                style = MaterialTheme.typography.bodySmall,
                color = JwTheme.colors.textSecondary,
            )
        }
        JwHorizontalDivider()
        JwListItem(
            text = stringResource(Res.string.oss_licenses),
            selected = false,
            onClick = onClickOSSLicenses,
            trailing = {
                JwIcon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = JwTheme.colors.textSecondary,
                    modifier = Modifier.padding(3.dp),
                )
            },
        )
    }
}
