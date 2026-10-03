package com.krafttools.langkraft.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Dark only, deliberately.
 *
 * This app is read in the dark — on a train, before a flight, in a queue — and every
 * other Kraft app is dark. There is no theme switch because there is nothing to switch
 * between, and offering one would imply a choice that does not exist.
 *
 * The palette is KraftDark, identical to EnglishKraft's, so the portfolio reads as one
 * body of work rather than a set of lookalikes.
 *
 * Contrast note: text carries everything. A phrasebook is a wall of script and romanised
 * text, and chrome that competes with it is chrome that costs legibility. Anything
 * decorative must be able to lose.
 */
private val KraftDark = darkColorScheme(
    primary = Color(0xFF9CCBFF),
    onPrimary = Color(0xFF00315C),
    primaryContainer = Color(0xFF00477F),
    onPrimaryContainer = Color(0xFFD3E4FF),

    secondary = Color(0xFFBBC7DB),
    onSecondary = Color(0xFF253140),
    secondaryContainer = Color(0xFF3B4858),
    onSecondaryContainer = Color(0xFFD7E3F7),

    tertiary = Color(0xFFD5BDE4),
    onTertiary = Color(0xFF38293F),
    tertiaryContainer = Color(0xFF4F3F57),
    onTertiaryContainer = Color(0xFFF1DAFF),

    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE2E6EC),
    surface = Color(0xFF0D1117),
    onSurface = Color(0xFFE2E6EC),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFF9AA4B2),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),

    outline = Color(0xFF3A424C),
)

@Composable
fun LangKraftTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = KraftDark, content = content)
}