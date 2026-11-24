package com.kedharsairam.langkraft.presentation.theme

import androidx.compose.ui.graphics.Color

// Dark-navy editorial system. One ground, one ink, one gold accent.
// Contrast: Ink 15.8:1 on Ground; Muted 7.1:1 on Ground; Accent 9.4:1 on Ground.
object LangColors {
    val Ground = Color(0xFF0A1220)
    val Surface = Color(0xFF121F33)          // card fill — always with a border
    val SurfaceAlt = Color(0xFF17253C)       // inset well / selected row
    val Ink = Color(0xFFF5F1E8)              // warm off-white
    val Muted = Color(0xFF9AA8BE)            // slate blue-grey
    val Accent = Color(0xFFE6B870)           // gold — the only accent
    val StateHard = Color(0xFFF2C57C)        // starred; close to gold by design
    val Border = Color(0xFF263954)           // quiet navy hairline

    // Compat aliases used by existing code.
    val Background = Ground
    val PrimaryText = Ink
    val SecondaryText = Muted
    val AccentGold = Accent
    val Separator = Border
}
