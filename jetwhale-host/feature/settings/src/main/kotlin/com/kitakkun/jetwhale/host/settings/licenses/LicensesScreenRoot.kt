package com.kitakkun.jetwhale.host.settings.licenses

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import soil.query.compose.rememberQuery

@Composable
context(screenContext: LicensesScreenContext)
fun LicensesScreenRoot(
    onClickBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SoilDataBoundary(
        state = rememberQuery(screenContext.librariesQueryKey),
    ) {
        LicensesScreen(
            libraries = it,
            onClickBack = onClickBack,
            modifier = modifier,
        )
    }
}
