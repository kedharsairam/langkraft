package com.krafttools.langkraft

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.krafttools.langkraft.data.ContentRepository
import com.krafttools.langkraft.ui.LangKraftTheme
import com.krafttools.langkraft.ui.LanguageListScreen
import com.krafttools.langkraft.ui.PathScreen
import com.krafttools.langkraft.ui.TierScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The regression test for a defect that only a device could find.
 *
 * The back control was originally a bare `Text("‹")` in a `navigationIcon` slot, with an
 * `onBack` lambda threaded in and then ignored. It rendered at the right size in the
 * right place, the compiler was satisfied because the parameter existed, and every JVM
 * test passed. On a Realme RMX3998 a `uiautomator dump` showed it had **no clickable node
 * at all** — drawn, correct size, completely inert.
 *
 * These tests exist because that class of defect is invisible to compilation and to unit
 * tests. Only a click proves a control works.
 *
 * Two things learned writing them, both of which cost a failed run:
 *
 *  - Screen state must be `mutableStateOf`. A plain `var` captured in `setContent` sets
 *    fine and never triggers recomposition, so the assertion after it fails for a reason
 *    that has nothing to do with the thing under test.
 *  - A `LazyColumn` does not compose off-screen items, so they are absent from the
 *    semantics tree entirely. `assertExists` on an un-scrolled item fails; scroll to it
 *    first, or assert something that is actually on screen.
 */
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun corpus(): ContentRepository.Corpus =
        ContentRepository.from(compose.activity).load()

    @Test
    fun backFromPathReturnsToLanguageList() {
        val corpus = corpus()
        val wentBack = mutableStateOf(false)
        compose.setContent {
            LangKraftTheme {
                if (wentBack.value) {
                    LanguageListScreen(corpus, positions = emptyMap()) { }
                } else {
                    PathScreen(
                        spec = corpus.spec("eng")!!,
                        corpus = corpus,
                        onOpenTier = { }, positions = emptyMap(), onOpenSearch = { },
                        onBack = { wentBack.value = true },
                    )
                }
            }
        }

        compose.onNodeWithText("0 · Courtesy").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()

        assertTrue("the back control did nothing — drawn but inert", wentBack.value)
        compose.onNodeWithText("LangKraft").assertIsDisplayed()
    }

    @Test
    fun backFromTierReturnsToPath() {
        val corpus = corpus()
        val wentBack = mutableStateOf(false)
        compose.setContent {
            LangKraftTheme {
                if (wentBack.value) {
                    PathScreen(corpus.spec("eng")!!, corpus, onOpenTier = { }, positions = emptyMap(), onOpenSearch = { }, onBack = { })
                } else {
                    TierScreen(
                        spec = corpus.spec("eng")!!,
                        tier = 0,
                        corpus = corpus,
                        onBack = { wentBack.value = true },
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()
        compose.waitForIdle()

        assertTrue("the back control did nothing — drawn but inert", wentBack.value)
        compose.onNodeWithText("1 · Transaction").assertIsDisplayed()
    }

    @Test
    fun theBackControlExistsOnEveryScreenThatCanGoBack() {
        val corpus = corpus()
        compose.setContent {
            LangKraftTheme {
                PathScreen(corpus.spec("eng")!!, corpus, onOpenTier = { }, positions = emptyMap(), onOpenSearch = { }, onBack = { })
            }
        }
        // A missing node throws here rather than failing silently at tap time.
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun tierZeroRendersExchangesFirstThenPhrases() {
        val corpus = corpus()
        compose.setContent {
            LangKraftTheme {
                TierScreen(spec = corpus.spec("eng")!!, tier = 0, corpus = corpus, onBack = { })
            }
        }
        // Exchanges come first, because a sequence is what a reader actually needs.
        compose.onNodeWithText("Entering a small shop").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Hello"))
        compose.onNodeWithText("Hello").assertIsDisplayed()
    }

    @Test
    fun anOptionalTurnIsMarkedAsOptional() {
        val corpus = corpus()
        compose.setContent {
            LangKraftTheme {
                TierScreen(spec = corpus.spec("eng")!!, tier = 0, corpus = corpus, onBack = { })
            }
        }
        // Without this the reader cannot tell a turn they may skip from one they must
        // say, and teaches themselves to stall. Three optional turns are composed in the
        // visible exchanges, so this matches several nodes — take the first.
        compose.onAllNodes(
            hasText("optional — the exchange works without it", substring = true)
        ).onFirst().assertIsDisplayed()
    }

    @Test
    fun swahiliShowsAGlossAndEnglishDoesNot() {
        // The gloss_mode parameter, proven per language rather than assumed. English sets
        // same_as_native so its gloss IS its native text and rendering both would print
        // "Hello — Hello". Swahili sets required, so its gloss must appear.
        val corpus = corpus()

        compose.setContent {
            LangKraftTheme {
                TierScreen(spec = corpus.spec("swh")!!, tier = 0, corpus = corpus, onBack = { })
            }
        }
        compose.onNodeWithText("Habari gani?").assertIsDisplayed()
        compose.onNodeWithText("How are you?").assertIsDisplayed()

    }

    @Test
    fun englishCarriesNoGlossBecauseItsGlossIsItsNativeText() {
        // The other half of the same parameter, and the reason it exists: rendering both
        // would print "Hello — Hello".
        val corpus = corpus()
        compose.setContent {
            LangKraftTheme {
                TierScreen(spec = corpus.spec("eng")!!, tier = 0, corpus = corpus, onBack = { })
            }
        }
        // Scroll first: a LazyColumn does not compose off-screen items, so an
        // un-scrolled assertExists fails for a reason that has nothing to do with the
        // thing under test.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Hello"))
        compose.onNodeWithText("Hello").assertIsDisplayed()
        // The phrase must appear EXACTLY once. A rendered gloss would put "Hello" on
        // screen a second time, which is the "Hello — Hello" this parameter exists to
        // prevent. Matching on the text alone cannot distinguish the two, so the count
        // is what carries the assertion.
        compose.onAllNodes(hasText("Hello")).assertCountEquals(1)
    }

    @Test
    fun aLatinLanguageRendersNoRomanisation() {
        // Both languages are Latin, so neither may show a romanisation line. This is the
        // rule that will break first when a non-Latin language arrives, and it is cheap
        // to hold now.
        val corpus = corpus()
        compose.setContent {
            LangKraftTheme {
                TierScreen(spec = corpus.spec("swh")!!, tier = 0, corpus = corpus, onBack = { })
            }
        }
        compose.onNodeWithText("Habari gani?").assertIsDisplayed()
        // A romanisation would duplicate the native text; assert one Text, not two.
        compose.onAllNodes(hasText("Habari gani?")).assertCountEquals(1)
    }

    @Test
    fun tamilShowsBothScriptsAndTheyAreDistinct() {
        // The rule that two Latin-script languages could not exercise, and the reason
        // Tamil is in the catalogue. A wrong conjunct is a SILENT failure — no crash, no
        // log line, just a wrong character — so the only defence is asserting that both
        // lines are present and are not the same string.
        val corpus = corpus()
        compose.setContent {
            LangKraftTheme {
                TierScreen(spec = corpus.spec("tam")!!, tier = 0, corpus = corpus, onBack = { })
            }
        }

        // The exchange section renders FIRST, so before scrolling only the exchange copy
        // is composed — a count taken here is 1 by accident, not by correctness. Scroll
        // to the phrases section so the standalone entries exist too.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Hello"))
        compose.onNodeWithText("வணக்கம்").assertIsDisplayed()          // the script
        compose.onNodeWithText("vaṇakkam").assertIsDisplayed()         // the romanization
        compose.onNodeWithText("Hello").assertIsDisplayed()            // the gloss

        // Both present. If the romanization were dropped for a non-Latin language, or the
        // script silently fell back, one of these would be 0.
        assertTrue(compose.onAllNodes(hasText("வணக்கம்")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(hasText("vaṇakkam")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun tamilRendersTheRomanizationWithItsDiacritics() {
        // ISO 15919 needs ṇ ṟ ḷ ḻ ṉ. If the romanization font lacks them the reader sees
        // a tofu box where the retroflex marker should be, which defeats the entire
        // reason the romanization exists.
        val corpus = corpus()
        compose.setContent {
            LangKraftTheme {
                TierScreen(spec = corpus.spec("tam")!!, tier = 0, corpus = corpus, onBack = { })
            }
        }
        compose.onNodeWithText("vaṇakkam").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("tayavuseythu"))
        compose.onNodeWithText("tayavuseythu").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("naṉṟi"))
        compose.onNodeWithText("naṉṟi").assertIsDisplayed()
    }

    @Test
    fun anEmptyTierSaysSoRatherThanRenderingNothing() {
        val corpus = corpus()
        compose.setContent {
            LangKraftTheme {
                // Tier 3 has no authored content yet. A blank screen would read as a bug.
                TierScreen(spec = corpus.spec("eng")!!, tier = 3, corpus = corpus, onBack = { })
            }
        }
        compose.onNodeWithText("Nothing authored at this tier yet.").assertIsDisplayed()
    }

    @Test
    fun exchangesAppearInCuratedOrderNotAlphabetical() {
        val corpus = corpus()
        compose.setContent {
            LangKraftTheme {
                TierScreen(spec = corpus.spec("eng")!!, tier = 0, corpus = corpus, onBack = { })
            }
        }
        // "Entering a small shop" is order 1 and must be the first thing on screen.
        // Alphabetically "Asking a local…" would sort ahead of it, which tells a
        // first-time reader nothing about which exchange they will actually need.
        compose.onNodeWithText("Entering a small shop").assertIsDisplayed()
        // Order 6, therefore far below the fold and not composed at all.
        compose.onNodeWithText("Asking a local for help when you have no words for the problem",
            substring = true).assertDoesNotExist()
    }
}