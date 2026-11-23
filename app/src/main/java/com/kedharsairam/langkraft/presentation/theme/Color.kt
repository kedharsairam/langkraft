package com.kedharsairam.langkraft.presentation.theme

import androidx.compose.ui.graphics.Color

// Locked Neo-Brutalism dark palette. Two inks, one accent — no third ink level.
// Verified grounds from the ArtCraft system; all other values derived by its rules.
object LangColors {
    val Ground = Color(0xFF0B0B0C)
    val Ink = Color(0xFFF2F1EE)
    val Surface = Color(0xFF141416)          // only with a border
    val Muted = Color(0xFF888888)            // 5.55:1 on ground
    val Accent = Color(0xFFE5C07B)            // gold — the only per-app accent

    // Compat aliases used by existing code.
    val Background = Ground
    val PrimaryText = Ink
    val SecondaryText = Muted
    val AccentGold = Accent
    val Border = Ink
    val Separator = Muted
}
