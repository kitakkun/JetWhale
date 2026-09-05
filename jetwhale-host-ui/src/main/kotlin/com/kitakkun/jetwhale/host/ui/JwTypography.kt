package com.kitakkun.jetwhale.host.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * A type scale sized for a desktop tool window: 13sp body text and 11–12sp labels, where Material's
 * mobile defaults start at 14–16sp. The names keep their Material 3 roles, so `MaterialTheme.typography`
 * inside a plugin keeps meaning what it always did — only the sizes change.
 */
public object JwTypography {
    /** Monospace style for identifiers, URLs, JSON and anything else read character by character. */
    public val code: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )

    public fun material(): Typography {
        val sans = FontFamily.Default
        return Typography(
            displayLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 48.sp),
            displayMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 40.sp),
            displaySmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 26.sp, lineHeight = 32.sp),
            headlineLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
            headlineMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
            headlineSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
            titleLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
            titleMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
            titleSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
            bodyLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
            bodyMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
            bodySmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
            labelLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
            labelMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
            labelSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
        )
    }
}
