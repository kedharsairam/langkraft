package com.krafttools.langkraft.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.krafttools.langkraft.R

/**
 * Bundled type for every script the app ships content in.
 *
 * **These fonts were not real until 2026-10-06.** The eight files previously in `res/font/`
 * were GitHub 404 HTML pages saved with a `.ttf` extension — all of them, within fifty bytes
 * of each other in size, each beginning `<!DOCTYPE html>` rather than an sfnt magic number.
 * They were committed, and the header comment in this file described a fallback problem they
 * were supposed to have fixed. Thai rendered on the device because Android supplied
 * Noto Serif Thai, which is precisely the substitution this file was written to prevent, so the
 * problem was documented and simultaneously unobserved.
 *
 * That is recorded here rather than deleted because the failure mode is the lesson: nothing
 * about "the font is bundled" is checkable by eye, and a plausible-looking file in the right
 * directory is not evidence. `pipeline/fonts/fetch.py` now verifies the sfnt magic and the
 * presence of a `cmap` table before accepting any download, and
 * `pipeline/content/font-coverage.py` proves every character the shipped content uses is
 * present in the face that will render it. Both fail loudly.
 *
 * **Why these are bundled rather than left to the system.** The device's default for
 * non-Latin text resolves to a serif face whose conventions differ from the sans Latin text
 * beside it — Noto Serif Thai carries loops and spurs where Thai's own convention is loopless,
 * so the two read as different typographic traditions. Beyond appearance, leaving the app's
 * most heavily-weighted element to system configuration means the look of the whole app can
 * change under the user. For a reference work that is read rather than interacted with, the
 * type should be fixed by the app.
 *
 * **Latin is deliberately NOT bundled.** Roboto is on every Android device, the Latin text is
 * the chrome rather than the content, and a Latin face would add weight for no gain.
 *
 * **Every face is subset to the characters the content uses.** Noto Sans SC is 17 MB, JP 9 MB
 * and KR 10 MB as shipped — 36 MB for a 1.8 MB application, to render roughly two hundred
 * characters per language. Subsetting brings all eight families to 1.75 MB, and shaping tables
 * (GSUB/GPOS) are retained because Devanagari conjunct formation and Arabic joining live there;
 * a subset that dropped them would render isolated letters correctly and break every word
 * that needs joining.
 *
 * SIL Open Font License 1.1. Recorded in-app and in `res/raw/`, as a licence obligation.
 */
private fun family(
    regular: Int,
    medium: Int,
    semibold: Int,
    bold: Int,
): FontFamily = FontFamily(
    Font(regular, FontWeight.Normal),
    Font(medium, FontWeight.Medium),
    Font(semibold, FontWeight.SemiBold),
    Font(bold, FontWeight.Bold),
)

val NotoSansArabic = family(
    R.font.notosansarabicregular,
    R.font.notosansarabicmedium,
    R.font.notosansarabicsemibold,
    R.font.notosansarabicbold,
)

val NotoSansCyrillic = family(
    R.font.notosanscyrillicregular,
    R.font.notosanscyrillicmedium,
    R.font.notosanscyrillicsemibold,
    R.font.notosanscyrillicbold,
)

val NotoSansDevanagari = family(
    R.font.notosansdevanagariregular,
    R.font.notosansdevanagarimedium,
    R.font.notosansdevanagarisemibold,
    R.font.notosansdevanagaribold,
)

val NotoSansHangul = family(
    R.font.notosanskrregular,
    R.font.notosanskrmedium,
    R.font.notosanskrsemibold,
    R.font.notosanskrbold,
)

val NotoSansHan = family(
    R.font.notosansscregular,
    R.font.notosansscmedium,
    R.font.notosansscsemibold,
    R.font.notosansscbold,
)

val NotoSansJapanese = family(
    R.font.notosansjpregular,
    R.font.notosansjpmedium,
    R.font.notosansjpsemibold,
    R.font.notosansjpbold,
)

val NotoSansThai = family(
    R.font.notosansthairegular,
    R.font.notosansthaimedium,
    R.font.notosansthaisemibold,
    R.font.notosansthaibold,
)

val NotoSansTamil = family(
    R.font.notosanstamilregular,
    R.font.notosanstamilmedium,
    R.font.notosanstamilsemibold,
    R.font.notosanstamilbold,
)

/**
 * The face for a language's own script.
 *
 * Spec-driven, not locale-driven, for the same reason the layout direction is: the script
 * belongs to the language being read, not to the phone's language. A Telugu reader on an
 * English phone must still get Noto Sans Tamil.
 *
 * Matching is on substrings rather than exact names because the specs record script names from
 * the Unicode range ("Devanagari", "Han", "Hangul", "Japanese") and exact equality silently
 * returned `FontFamily.Default` for several of them — the same class of bug as a font file that
 * is not a font: the code path runs, produces a value, and the value is wrong.
 */
fun scriptFontFamily(primaryScript: String): FontFamily {
    val s = primaryScript
    return when {
        s.contains("Arabic", ignoreCase = true) -> NotoSansArabic
        s.contains("Devanagari", ignoreCase = true) -> NotoSansDevanagari
        s.contains("Hangul", ignoreCase = true) -> NotoSansHangul
        // Checked BEFORE Han: Noto Sans JP carries kana and kanji and is the better face for
        // Japanese text, while a Japanese phrase containing one kanji must not be rendered by
        // the Simplified Chinese face.
        s.contains("Japanese", ignoreCase = true) || s.contains("Kana", ignoreCase = true) ->
            NotoSansJapanese
        s.contains("Han", ignoreCase = true) || s.contains("CJK", ignoreCase = true) ->
            NotoSansHan
        s.contains("Cyrillic", ignoreCase = true) -> NotoSansCyrillic
        s.contains("Thai", ignoreCase = true) -> NotoSansThai
        s.contains("Tamil", ignoreCase = true) -> NotoSansTamil
        else -> FontFamily.Default
    }
}