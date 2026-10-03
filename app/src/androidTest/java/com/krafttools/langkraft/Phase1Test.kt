package com.krafttools.langkraft

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.krafttools.langkraft.data.ContentRepository
import com.krafttools.langkraft.data.ProgressStore
import com.krafttools.langkraft.data.SearchIndex
import com.krafttools.langkraft.ui.LangKraftTheme
import com.krafttools.langkraft.ui.PathScreen
import com.krafttools.langkraft.ui.SearchScreen
import com.krafttools.langkraft.ui.TierScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 1: the bookmark, the failure flag, and search.
 *
 * The tests around the flag are about two things: that it writes exactly one row, and that
 * writing it changes nothing the learner can see. A flag that produced a badge or a counter
 * would turn a complaint into a score, which is precisely what the product rule forbids.
 */
@RunWith(AndroidJUnit4::class)
class Phase1Test {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun corpus() = ContentRepository.from(compose.activity).load()

    // ---- the bookmark ----------------------------------------------------

    @Test
    fun progressStoreRoundTripsAPosition() {
        val db = ProgressStore(compose.activity)
        db.clearPosition("zz")
        assertEquals(0, db.position("zz", 0))
        db.setPosition("zz", 0, 17)
        assertEquals(17, db.position("zz", 0))
        // Separate tiers must not collide.
        db.setPosition("zz", 1, 3)
        assertEquals(17, db.position("zz", 0))
        assertEquals(3, db.position("zz", 1))
        db.clearPosition("zz")
    }

    @Test
    fun progressStoreKeepsEveryPositionForTheBookmark() {
        val db = ProgressStore(compose.activity)
        db.clearPosition("zz2")
        db.setPosition("zz2", 0, 5)
        db.setPosition("zz2", 1, 9)
        val all = db.allPositions()["zz2"]
        assertNotNull("a language with positions must appear in allPositions", all)
        assertEquals(5, all!![0])
        assertEquals(9, all[1])
        db.clearPosition("zz2")
    }

    @Test
    fun thereIsNoAccuracyOrCountColumnToAccidentallyAddLater() {
        // The product rule is that progress means POSITION only. A schema with a `correct`
        // or a `count` column would make a streak or a score one line away, so the test
        // asserts the absence rather than trusting a code comment.
        val db = ProgressStore(compose.activity)
        db.clearPosition("zz3")
        db.setPosition("zz3", 0, 4)
        val cols = mutableListOf<String>()
        db.readableDatabase.rawQuery("PRAGMA table_info(position)", null).use { c ->
            while (c.moveToNext()) cols.add(c.getString(1))
        }
        assertEquals(listOf("lang", "tier", "item_index", "updated_at"), cols)
        db.clearPosition("zz3")
    }

    // ---- the one input ---------------------------------------------------

    @Test
    fun aFlagWritesExactlyOneRowAndASecondTapIsKept() {
        val db = ProgressStore(compose.activity)
        db.clearFlags("zz4")
        db.addFlag("zz4", "zz4-0001", "Morocco")
        assertEquals(1, db.flagCount("zz4"))
        // A second observation is real information and must not be de-duplicated:
        // "this failed me twice in Morocco" is stronger than either alone.
        db.addFlag("zz4", "zz4-0001", "Morocco")
        assertEquals(2, db.flagCount("zz4"))
        // The country is optional and null must not become an empty string.
        db.addFlag("zz4", "zz4-0001", null)
        assertEquals(3, db.flagCount("zz4"))
        val flags = db.flagsFor("zz4")
        assertEquals("zz4-0001", flags.first().entryId)
        assertEquals(3, flags.size)
        db.clearFlags("zz4")
    }

    // ---- search ----------------------------------------------------------

    @Test
    fun searchFindsByRomanizationMeaningAndScript() {
        val index = SearchIndex(corpus())
        // Non-Latin, found by sound rather than by shape.
        assertTrue(index.search("tayavuseythu").any { it.entry.lang == "tam" })
        // Found by meaning.
        assertTrue(index.search("thank you").any { it.entry.lang == "swh" })
        // Found by the native script itself.
        assertTrue(index.search("வணக்கம்").isNotEmpty())
    }

    @Test
    fun searchRanksExactAbovePrefixAboveSubstring() {
        val index = SearchIndex(corpus())
        val hits = index.search("hello", lang = "eng")
        assertTrue(hits.isNotEmpty())
        assertEquals("hello", hits.first().entry.textNative.lowercase())
    }

    @Test
    fun searchIsScopedToALanguageWhenAsked() {
        val index = SearchIndex(corpus())
        assertTrue(index.search("thank", lang = "swh").all { it.entry.lang == "swh" })
    }

    @Test
    fun anEmptyQueryReturnsNothing() {
        val index = SearchIndex(corpus())
        assertTrue(index.search("").isEmpty())
        assertTrue(index.search("   ").isEmpty())
    }

    @Test
    fun aNullRomanizationNeverMatches() {
        // A null romanization must not become "" and match every query — which would make
        // every Latin-script entry match a one-character query.
        val index = SearchIndex(corpus())
        val hits = index.search("z", lang = "eng", limit = 40)
        assertTrue(hits.all { it.matchedOn.label != "romanization" })
    }

    @Test
    fun searchScreenExplainsItselfWhenEmpty() {
        val c = corpus()
        compose.setContent {
            LangKraftTheme {
                SearchScreen(
                    index = remember(c) { SearchIndex(c) }, spec = c.spec("eng")!!,
                    onFlag = { _, _ -> }, onBack = { },
                )
            }
        }
        compose.onNodeWithText("Search by what you hear, what it looks like, or what it means.")
            .assertIsDisplayed()
    }

    @Test
    fun searchScreenReportsNoMatchRatherThanShowingNothing() {
        val c = corpus()
        compose.setContent {
            LangKraftTheme {
                SearchScreen(
                    index = remember(c) { SearchIndex(c) }, spec = c.spec("eng")!!,
                    onFlag = { _, _ -> }, onBack = { },
                )
            }
        }
        // SetText via the semantics action rather than performTextInput. The latter needs
        // a live IME, which a Compose test does not have, so it silently does nothing and
        // the assertion then fails for a reason unrelated to the app.
        compose.onNode(hasSetTextAction()).performSemanticsAction(SemanticsActions.SetText) {
            it(AnnotatedString("zzzznotathing"))
        }
        compose.waitForIdle()
        // First: did SetText actually land? Assert on the field, whose value is
        // unambiguous. If this fails the mechanism is the problem; if it passes and the
        // next line fails, the no-match branch is.
        compose.onNode(hasSetTextAction()).assert(hasText("zzzznotathing"))
        // substring = true. hasText defaults to EXACT match, and the rendered string is
        // `Nothing matches "zzzznotathing".` — an exact matcher never matches a prefix.
        compose.onNode(hasText("Nothing matches", substring = true)).assertIsDisplayed()
    }

    // ---- the bookmark in the UI -----------------------------------------

    @Test
    fun thePathScreenShowsABookmarkOnlyWhenOneExists() {
        val c = corpus()
        val spec = c.spec("eng")!!
        val withBookmark = mutableStateOf(false)
        compose.setContent {
            LangKraftTheme {
                PathScreen(
                    spec = spec, corpus = c,
                    positions = if (withBookmark.value) mapOf("eng" to mapOf(0 to 12)) else emptyMap(),
                    onOpenTier = { }, onOpenSearch = { }, onBack = { },
                )
            }
        }
        // Absent when there is no position. A bookmark shown at zero would be a lie.
        compose.onNodeWithText("Pick up where you stopped").assertDoesNotExist()

        compose.runOnUiThread { withBookmark.value = true }
        compose.waitForIdle()
        compose.onNodeWithText("Pick up where you stopped").assertExists()
    }

    /**
     * Regression test for a bug that shipped silently.
     *
     * `TierScreen` accepted `startIndex` and `onPosition` and then used neither. The
     * parameter existed, every call site compiled, no test failed — and the bookmark was
     * dead: nothing was ever written and nothing was ever restored. A parameter that is
     * declared and ignored is invisible to the compiler, so the only defence is a test
     * that scrolls and asserts the callback fired.
     */
    @Test
    fun theTierScreenReportsWhereTheLearnerScrolledTo() {
        val c = corpus()
        val spec = c.spec("eng")!!
        val reported = mutableListOf<Int>()
        compose.setContent {
            LangKraftTheme {
                TierScreen(
                    spec = spec, tier = 0, corpus = c,
                    onPosition = { reported.add(it) },
                    onBack = { },
                )
            }
        }
        compose.waitForIdle()
        // Opening must NOT report. A position records that the learner MOVED; the initial
        // emission is just the seed, and persisting it meant a stored bookmark was
        // destroyed by the act of restoring it.
        assertTrue("opening the screen must not report a position, got $reported", reported.isEmpty())

        compose.onNode(hasScrollAction()).performScrollToIndex(30)
        compose.waitForIdle()
        assertTrue(
            "scrolling to index 30 must report it, got $reported",
            reported.any { it >= 25 },
        )
    }

    /**
     * A stored position must actually restore the list, not merely be accepted.
     *
     * Discriminating this is harder than it looks. `performScrollToIndex(31)` would report
     * 31 whether the list opened at 30 or at 0, so it cannot tell the two apart. A small
     * swipe can: from 30 it lands somewhere past 30, from 0 it lands in single digits.
     */
    @Test
    fun aStoredPositionRestoresTheTierToWhereItWasLeft() {
        val c = corpus()
        val spec = c.spec("eng")!!
        val reported = mutableListOf<Int>()
        compose.setContent {
            LangKraftTheme {
                TierScreen(
                    spec = spec, tier = 0, corpus = c,
                    startIndex = 30,
                    onPosition = { reported.add(it) },
                    onBack = { },
                )
            }
        }
        compose.waitForIdle()
        compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertTrue(
            "list should have opened at 30, so a small swipe reports past it; got $reported",
            reported.any { it >= 30 },
        )
    }

    /**
     * A bookmark stored against a larger content set must not brick or corrupt the screen.
     *
     * This is the real upgrade path: the learner had 200 items, an update ships fewer, and
     * the stored index is now out of range. It used to be passed through unclamped — and
     * because the collector started on compose, the out-of-range value was written straight
     * back, so the bad bookmark never healed.
     */
    @Test
    fun aStoredPositionBeyondTheEndIsClampedRatherThanFatal() {
        val c = corpus()
        val spec = c.spec("eng")!!
        val reported = mutableListOf<Int>()
        compose.setContent {
            LangKraftTheme {
                TierScreen(
                    spec = spec, tier = 0, corpus = c,
                    startIndex = 9999,
                    onPosition = { reported.add(it) },
                    onBack = { },
                )
            }
        }
        compose.waitForIdle()
        compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        compose.waitForIdle()
        // Whatever it settled on, it must be a real index — never 9999 handed back out.
        assertTrue("out-of-range index must be clamped, got $reported", reported.none { it == 9999 })
    }

    /** A tier with nothing authored in it must not crash on an out-of-range bookmark. */
    @Test
    fun aStoredPositionOnAnEmptyTierIsHarmless() {
        val c = corpus()
        val spec = c.spec("eng")!!
        compose.setContent {
            LangKraftTheme {
                TierScreen(
                    spec = spec, tier = 3, corpus = c,   // no Tier 3 content ships
                    startIndex = 500,
                    onPosition = { },
                    onBack = { },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Nothing authored at this tier yet.").assertExists()
    }
}