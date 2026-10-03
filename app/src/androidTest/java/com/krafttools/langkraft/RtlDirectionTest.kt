package com.krafttools.langkraft

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.krafttools.langkraft.data.ContentRepository
import com.krafttools.langkraft.data.Direction as ItemDirection
import com.krafttools.langkraft.data.Entry
import com.krafttools.langkraft.data.Exchange
import com.krafttools.langkraft.data.ExchangeTurn
import com.krafttools.langkraft.data.LanguageSpec
import com.krafttools.langkraft.data.SourceRef
import com.krafttools.langkraft.data.Tier
import com.krafttools.langkraft.data.Variant
import com.krafttools.langkraft.ui.LangKraftTheme
import com.krafttools.langkraft.ui.TierScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.activity.ComponentActivity

/**
 * RTL is a correctness requirement, not a feature, and it is invisible until a language
 * needs it. Arabic, Dari and Urdu are all on the list, and none of them has ever been
 * rendered: `grep` for LayoutDirection across the app source returned nothing at all.
 *
 * The corpus here is SYNTHETIC and never ships. It exists so the direction path can be
 * exercised without adding a language to the catalogue, and because a test that depends on
 * catalog content breaks every time that content is revised.
 */
@RunWith(AndroidJUnit4::class)
class RtlDirectionTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val src = SourceRef("authored", "fixture", "CC BY-SA 4.0")

    private val rtlSpec = LanguageSpec(
        code = "zzr", name = "Fixture RTL", endonym = "fixture",
        romanization = "chat_alphabet", role = "course", glossMode = "required",
        defaultVariety = "ar-JO",
        variants = listOf(Variant("ar-JO", "Levantine")),
        scriptPrimary = "Arabic", scriptDirection = "rtl",
        registerSystem = "politeness_levels",
        tiers = listOf(
            Tier(0, "Courtesy", "Not rude.", 2, "high"),
            Tier(1, "Transaction", "It works.", 0, "medium"),
        ),
    )

    private val rtlCorpus = ContentRepository.Corpus(
        specs = listOf(rtlSpec),
        entries = listOf(
            Entry(
                id = "zzr-0001", lang = "zzr", tier = 0, domain = 1,
                textNative = "مرحبا", textRomanized = "marhaba", textEnglish = "Hello",
                register = "neutral", direction = ItemDirection.SAY,
                why = "The greeting.", exchangeId = null, exchangeTurn = null,
                caution = "Register matters more than grammar in Arabic.",
                source = src, failureFlags = emptyList(),
            ),
            Entry(
                id = "zzr-0002", lang = "zzr", tier = 0, domain = 1,
                textNative = "شكرا", textRomanized = "shukran", textEnglish = "Thank you",
                register = "neutral", direction = ItemDirection.SAY,
                why = "Thanks.", exchangeId = null, exchangeTurn = null, caution = null,
                source = src, failureFlags = emptyList(),
            ),
        ),
        exchanges = listOf(
            Exchange(
                id = "zzr-x0001", lang = "zzr", tier = 0, domain = 1,
                scenario = "Greeting someone", order = 1,
                turns = listOf(
                    ExchangeTurn(1, "you", ItemDirection.SAY, null, "مرحبا", "marhaba", "Hello", false),
                    ExchangeTurn(2, "them", ItemDirection.UNDERSTAND, "zzr-0002", "شكرا", "shukran", "Thank you", false),
                ),
                source = src, failureFlags = emptyList(),
            ),
        ),
    )

    @Test
    fun tierScreenAdoptsTheSpecsWritingDirection() {
        var seen: LayoutDirection? = null
        compose.setContent {
            LangKraftTheme {
                Column {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        // Read the direction from INSIDE the composition — it is the only
                        // place LocalLayoutDirection.current is meaningful.
                        seen = LocalLayoutDirection.current
                        TierScreen(spec = rtlSpec, tier = 0, corpus = rtlCorpus, onBack = { })
                    }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(LayoutDirection.Rtl, seen)
    }

    @Test
    fun rtlScriptRomanizationAndGlossAllRender() {
        compose.setContent {
            LangKraftTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    TierScreen(spec = rtlSpec, tier = 0, corpus = rtlCorpus, onBack = { })
                }
            }
        }
        // "مرحبا" legitimately appears twice — once as a standalone entry and once as
        // turn 1 of the exchange — so the single-node matcher would fail on correct
        // behaviour. Take the first.
        compose.onAllNodes(hasText("مرحبا")).onFirst().assertIsDisplayed()
        compose.onAllNodes(hasText("marhaba")).onFirst().assertIsDisplayed()
        compose.onAllNodes(hasText("Hello")).onFirst().assertIsDisplayed()
    }

    @Test
    fun latinLanguagesStayLeftToRight() {
        val corpus = ContentRepository.from(compose.activity).load()
        val ltr = corpus.specs.filter { it.scriptDirection == "ltr" }
        assertTrue("the shipped catalogue must have LTR languages", ltr.isNotEmpty())
        assertTrue(
            "no shipped language may declare rtl until one actually does",
            corpus.specs.none { it.scriptDirection == "rtl" },
        )
    }

    @Test
    fun everyShippedSpecDeclaresADirection() {
        // A spec with no direction would silently default to LTR, which is the exact
        // failure that would hit Arabic on the day it was added.
        val corpus = ContentRepository.from(compose.activity).load()
        corpus.specs.forEach {
            assertTrue(
                "spec ${it.code} has no script direction",
                it.scriptDirection == "ltr" || it.scriptDirection == "rtl",
            )
        }
        assertEquals("catalogue size changed; update this test deliberately", 3, corpus.specs.size)
    }
}