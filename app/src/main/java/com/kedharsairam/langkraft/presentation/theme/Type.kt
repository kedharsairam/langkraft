package com.kedharsairam.langkraft.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.unit.sp
import com.kedharsairam.langkraft.R

// Bundled OFL fonts — no network, full Latin Extended coverage measured from cmap.
private val spaceGrotesk = FontFamily(
    Font(R.font.space_grotesk, weight = FontWeight.Normal)
)
private val inter = FontFamily(
    Font(R.font.inter, weight = FontWeight.Normal)
)
private val sourceCodePro = FontFamily(
    Font(R.font.source_code_pro, weight = FontWeight.Normal)
)

object KraftFonts {
    val Display = spaceGrotesk
    val Body = inter
    val Mono = sourceCodePro
}

// Type scale — see type_icons.md for 1.0x and 2.0x computed sizes.
val LangTypography = Typography(
    displayLarge = TextStyle(fontFamily = spaceGrotesk, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = spaceGrotesk, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = spaceGrotesk, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontFamily = spaceGrotesk, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = inter, fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = inter, fontSize = 16.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = inter, fontSize = 15.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = sourceCodePro, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = sourceCodePro, fontSize = 12.sp, lineHeight = 16.sp)
)

/** The phrase the learner says — mono, hero-sized, stress in CAPS. */
val YouSayStyle = TextStyle(
    fontFamily = sourceCodePro, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold
)

/** The target-language spelling — mono, readable. */
val RomanizationStyle = TextStyle(
    fontFamily = sourceCodePro, fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal
)

val MonoNumeric = TextStyle(
    fontFamily = sourceCodePro, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold
)

/** Mono caps label — labels only, never sentences. */
val MonoLabelStyle = TextStyle(
    fontFamily = sourceCodePro, fontSize = 12.sp, lineHeight = 16.sp,
    fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp
)

object PhraseSizes {
    val English = 16.sp
    val Romanization = 18.sp
    val YouSay = 22.sp
    val Meta = 12.sp
}
