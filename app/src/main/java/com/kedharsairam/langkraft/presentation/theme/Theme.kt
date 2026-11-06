package com.kedharsairam.langkraft.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun LangKraftTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = LangColors.DarkPrimary,
            onPrimary = LangColors.DarkOnPrimary,
            surface = LangColors.DarkSurface,
            onSurface = LangColors.DarkOnSurface,
            surfaceVariant = LangColors.DarkSurfaceVariant,
            onSurfaceVariant = LangColors.DarkOnSurfaceVariant,
            outline = LangColors.DarkOutline
        )
    } else {
        lightColorScheme(
            primary = LangColors.LightPrimary,
            onPrimary = LangColors.LightOnPrimary,
            surface = LangColors.LightSurface,
            onSurface = LangColors.LightOnSurface,
            surfaceVariant = LangColors.LightSurfaceVariant,
            onSurfaceVariant = LangColors.LightOnSurfaceVariant,
            outline = LangColors.LightOutline
        )
    }
    MaterialTheme(colorScheme = colors, typography = LangTypography, content = content)
}
