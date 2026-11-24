package com.kedharsairam.langkraft.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kedharsairam.langkraft.R

// Bundled OFL fonts — offline, full Latin Extended coverage verified against all 19 languages.
private val serifRegular = Font(R.font.source_serif_4_regular, weight = FontWeight.Normal)
private val serifBold = Font(R.font.source_serif_4_bold, weight = FontWeight.Bold)
private val serifItalic = Font(R.font.source_serif_4_italic, weight = FontWeight.Normal, style = FontStyle.Italic)
private val sans = Font(R.font.inter, weight = FontWeight.Normal)
private val mono = Font(R.font.source_code_pro, weight = FontWeight.Normal)

object KraftFonts {
    val Serif = FontFamily(serifRegular, serifBold, serifItalic)
    val Body = FontFamily(sans)
    val Mono = FontFamily(mono)
}

/** Editorial type scale — serif carries meaning, sans carries detail, mono carries labels. */
val LangTypography = Typography(
    displayLarge = TextStyle(fontFamily = KraftFonts.Serif, fontSize = 44.sp, lineHeight = 50.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = KraftFonts.Serif, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = KraftFonts.Serif, fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontFamily = KraftFonts.Serif, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontFamily = KraftFonts.Body, fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = KraftFonts.Body, fontSize = 16.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = KraftFonts.Body, fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = KraftFonts.Mono, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = KraftFonts.Mono, fontSize = 12.sp, lineHeight = 17.sp)
)

/** The phrase to say — large serif, stress in CAPS. */
val YouSayStyle = TextStyle(
    fontFamily = KraftFonts.Serif, fontSize = 26.sp, lineHeight = 34.sp,
    fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic
)

/** The target-language romanization — the hero of the card. */
val RomanizationStyle = TextStyle(
    fontFamily = KraftFonts.Serif, fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold
)

val MonoNumeric = TextStyle(
    fontFamily = KraftFonts.Mono, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal
)

/** Mono caps label — labels only, never sentences. */
val MonoLabelStyle = TextStyle(
    fontFamily = KraftFonts.Mono, fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 1.sp
)

object PhraseSizes {
    val English = 16.sp
    val Romanization = 22.sp
    val YouSay = 26.sp
    val Meta = 12.sp
}
