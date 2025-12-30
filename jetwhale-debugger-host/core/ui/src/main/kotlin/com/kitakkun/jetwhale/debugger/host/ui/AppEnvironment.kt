package com.kitakkun.jetwhale.debugger.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.kitakkun.jetwhale.debugger.host.model.AppLanguage
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
        if (default == null) {
            default = Locale.getDefault()
        }
        val new = when (value) {
            null -> default!!
            else -> Locale(value)
        }
        Locale.setDefault(new)
        return LocalAppLocale.provides(new.toString())
    }
}

@Composable
fun AppEnvironment(
    appLanguage: AppLanguage,
    content: @Composable () -> Unit
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
