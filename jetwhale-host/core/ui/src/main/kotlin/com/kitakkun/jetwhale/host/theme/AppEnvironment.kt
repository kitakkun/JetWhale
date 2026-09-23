package com.kitakkun.jetwhale.host.theme

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.model.AppLanguage
import java.util.Locale

/**
 * FYI: https://kotlinlang.org/docs/multiplatform/compose-resource-environment.html#locale
 */
object LocalAppLocale {
    private var default: Locale? = null
    private val LocalAppLocale = staticCompositionLocalOf { Locale.getDefault().toString() }
    val current: String
        @Composable get() = LocalAppLocale.current

    @Composable
    infix fun provides(value: String?): ProvidedValue<*> {
        val fallback = default ?: Locale.getDefault().also { default = it }
        val new = when (value) {
            null -> fallback
            else -> Locale(value)
        }
        Locale.setDefault(new)
        return LocalAppLocale.provides(new.toString())
    }
}

@Composable
fun AppEnvironment(
    appLanguage: AppLanguage,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAppLocale provides appLanguage.toLocaleString(),
    ) {
        content()
    }
}

private fun AppLanguage.toLocaleString(): String? = when (this) {
    AppLanguage.English -> "en"
    AppLanguage.Japanese -> "ja"
}

@Preview
@Composable
private fun AppEnvironmentPreview() {
    AppEnvironment(appLanguage = AppLanguage.English) {
        Text(text = LocalAppLocale.current)
    }
}
