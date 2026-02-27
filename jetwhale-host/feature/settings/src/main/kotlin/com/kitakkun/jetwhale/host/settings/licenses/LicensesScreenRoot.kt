package com.kitakkun.jetwhale.host.settings.licenses

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import soil.query.compose.rememberQuery

@Composable
context(screenContext: LicensesScreenContext)
fun LicensesScreenRoot(
    onClickBack: () -> Unit,
) {
    SoilDataBoundary(
        state = rememberQuery(screenContext.librariesQueryKey),
    ) {
        LicensesScreen(
            libraries = it,
            onClickBack = onClickBack,
        )
    }
}
