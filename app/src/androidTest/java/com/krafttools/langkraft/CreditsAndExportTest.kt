package com.krafttools.langkraft

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.krafttools.langkraft.data.ContentRepository
import com.krafttools.langkraft.data.Credit
import com.krafttools.langkraft.data.Entry
import com.krafttools.langkraft.data.FailureFlag
import com.krafttools.langkraft.data.FlagExport
import com.krafttools.langkraft.data.ProgressStore
import com.krafttools.langkraft.data.SourceRef
import com.krafttools.langkraft.ui.CreditsScreen
import com.krafttools.langkraft.ui.LangKraftTheme
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The credits screen and the flag export.
 *
 * Both exist because of obligations rather than features. CC BY-SA content was being
 * redistributed with nowhere in the app to credit it, and the bundled Noto families added a
 * second obligation under a licence that requires the text to travel with the font. The
 * failure flag -- the app's only permitted input -- wrote rows that nothing could read, so
 * the loop the product is built around was open at both ends.
 */
@RunWith(AndroidJUnit4::class)
class CreditsAndExportTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun corpus() = ContentRepository.from(compose.activity).load()

    // ---- the obligation itself -------------------------------------------

    @Test
    fun everyLanguageWithAttributionCarriesIt() {
        // Thai and Swahili draw on Wikivoyage (CC BY-SA) and Tatoeba (CC BY 2.0 FR). If a
        // spec declares attribution and the emitter drops it, the app ships unattributed.
        val c = corpus()
        val withAttribution = c.specs.filter { it.attribution.isNotEmpty() }
        assertTrue("Thai must carry attribution", c.spec("tha")!!.attribution.isNotEmpty())
        assertTrue("at least two languages carry attribution", withAttribution.size >= 2)
    }

    @Test
    fun everyAttributionNamesALicence() {
        // A credit without a licence is not a credit.
        for (spec in corpus().specs) {
            for (a in spec.attribution) {
                assertTrue("${spec.code}: '${a.source}' has no licence", a.licence.isNotBlank())
                assertFalse(
                    "${spec.code}: '${a.source}' licence is a placeholder",
                    a.licence.equals("Unknown", ignoreCase = true),
                )
            }
        }
    }

    @Test
    fun theBundledFontsAreCredited() {
        // The SIL OFL requires the licence to travel with the font. Not crediting Google
        // for two bundled families is the specific breach this test exists to prevent.
        val credits = corpus().credits
        val fonts = credits.firstOrNull { it.source.contains("Noto", ignoreCase = true) }
        assertTrue("the bundled fonts must be credited", fonts != null)
        assertTrue(
            "the OFL must be named, was '${fonts!!.licence}'",
            fonts.licence.contains("SIL Open Font License"),
        )
    }

    @Test
    fun theCreditsScreenNamesTheLicencesItExistsToDischarge() {
        val c = corpus()
        compose.setContent {
            LangKraftTheme {
                CreditsScreen(appCredits = c.credits, specs = c.specs, onBack = { })
            }
        }
        compose.onNodeWithText("SIL Open Font License 1.1").assertExists()
        compose.onNodeWithText("CC BY-SA 4.0").assertExists()
    }

    // ---- the export -------------------------------------------------------

    private fun entry(id: String, lang: String, native: String, gloss: String) = Entry(
        id = id, lang = lang, tier = 0, domain = 1,
        textNative = native, textRomanized = null, textEnglish = gloss,
        register = "neutral", direction = com.krafttools.langkraft.data.Direction.SAY,
        why = "test", exchangeId = null, exchangeTurn = null, caution = null,
        source = SourceRef("curated", "Wikivoyage: Thai phrasebook", "CC BY-SA 4.0"),
        failureFlags = emptyList(),
    )

    @Test
    fun theExportResolvesNamespacedFlagIdsBackToThePhrase() {
        // Flags are written as "entry:tha-0012". An export carrying that internal key
        // instead of the phrase would be useless to the person revising the spec, which is
        // the entire point of collecting them.
        val c = corpus()
        val target = c.entriesFor("tha", 0).first()
        val flags = mapOf(
            "tha" to listOf(
                FailureFlag(entryId = "entry:${target.id}", country = "Morocco", at = "2026-10-05T00:00:00Z"),
            )
        )
        val doc = JSONObject(FlagExport.buildDocument(c, flags))
        val row = doc.getJSONArray("flags").getJSONObject(0)
        assertEquals(target.id, row.getString("entry_id"))
        assertEquals(target.textNative, row.getString("text_native"))
        assertEquals(target.textEnglish, row.getString("text_english"))
        assertEquals("Morocco", row.getString("country"))
        assertEquals("entry", row.getString("kind"))
    }

    @Test
    fun theExportCarriesProvenanceBecauseTatoebaNamesAnAuthorPerSentence() {
        val c = corpus()
        val target = c.entriesFor("tha", 0).first()
        val flags = mapOf(
            "tha" to listOf(
                FailureFlag(entryId = "entry:${target.id}", country = null, at = "2026-10-05T00:00:00Z"),
            )
        )
        val row = JSONObject(FlagExport.buildDocument(c, flags))
            .getJSONArray("flags").getJSONObject(0)
        assertTrue("source must travel with the row", row.getString("source_id").isNotBlank())
        assertTrue("licence must travel with the row", row.getString("source_licence").isNotBlank())
        // A flag with no country is legitimate, and must not become an empty string.
        assertTrue("absent country must be null, not \"\"", row.isNull("country"))
    }

    @Test
    fun theExportIncludesEveryFlaggedLanguage() {
        val c = corpus()
        val a = c.entriesFor("tha", 0).first()
        val b = c.entriesFor("swh", 0).first()
        val flags = mapOf(
            "tha" to listOf(FailureFlag("entry:${a.id}", "Thailand", "2026-10-05T00:00:00Z")),
            "swh" to listOf(FailureFlag("entry:${b.id}", "Kenya", "2026-10-05T00:00:00Z")),
        )
        val doc = JSONObject(FlagExport.buildDocument(c, flags))
        assertEquals(2, doc.getInt("flag_count"))
        val langs = (0 until doc.getJSONArray("flags").length())
            .map { doc.getJSONArray("flags").getJSONObject(it).getString("language") }
            .toSet()
        assertEquals(setOf("tha", "swh"), langs)
    }

    @Test
    fun anEmptyExportIsValidRatherThanBroken() {
        val doc = JSONObject(FlagExport.buildDocument(corpus(), emptyMap()))
        assertEquals(0, doc.getInt("flag_count"))
        assertEquals("langkraft-failure-flags", doc.getString("format"))
    }

    @Test
    fun anUnresolvableFlagIdIsKeptRatherThanDropped() {
        // A dangling reference is information: it means the phrase was removed from the
        // content but the reader still failed at it. Silently dropping the row would
        // understate the problem.
        val c = corpus()
        val flags = mapOf(
            "tha" to listOf(FailureFlag("entry:tha-9999", null, "2026-10-05T00:00:00Z")),
        )
        val doc = JSONObject(FlagExport.buildDocument(c, flags))
        assertEquals(1, doc.getInt("flag_count"))
        assertEquals("tha-9999", doc.getJSONArray("flags").getJSONObject(0).getString("entry_id"))
    }
}