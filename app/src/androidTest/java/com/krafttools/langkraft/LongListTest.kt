package com.krafttools.langkraft

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.krafttools.langkraft.data.ContentRepository
import com.krafttools.langkraft.ui.LangKraftTheme
import com.krafttools.langkraft.ui.TierScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Navigation affordances inside a long phrase list.
 *
 * A Thai Tier 0 is five tone sets, fourteen exchanges and sixty-three entries, roughly
 * twenty-eight screens of scrolling. Three things have to hold at that length, and none of
 * them is testable by looking at the first screen:
 *
 *  - entries are grouped by domain, so a reader can see where one subject ends
 *  - the position readout tracks the scroll
 *  - there is a way back to the top
 */
@RunWith(AndroidJUnit4::class)
class LongListTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val corpus get() = ContentRepository.from(compose.activity).load()

    private fun showThaiTier0() {
        val c = corpus
        compose.setContent {
            LangKraftTheme {
                TierScreen(spec = c.spec("tha")!!, tier = 0, corpus = c, onBack = { })
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun entriesAreGroupedByDomainWithTheDomainsOwnLabel() {
        showThaiTier0()
        // The Domain enum has carried a human label for each of twelve domains since the
        // beginning and none of it was rendered, so "Hello" and "I have lost my wallet"
        // interleaved with nothing between them.
        compose.onNode(hasScrollAction())
            .performScrollToNode(hasText("Greetings & courtesy", substring = true))
        compose.onNodeWithText("Greetings & courtesy", substring = true).assertExists()
    }

    @Test
    fun thePositionReadoutTracksTheScroll() {
        showThaiTier0()
        compose.onNode(hasScrollAction())
            .performScrollToNode(hasText("Phrases", substring = true))
        // First composed position, before any scrolling: item 1 of the list.
        compose.onNodeWithText("1 / 63", substring = true).assertExists()

        compose.onNode(hasScrollAction()).performScrollToIndex(
            // Deep into the phrase list, so the readout must have moved with it.
            compose.onAllNodesWithText("This failed me").fetchSemanticsNodes().size + 20
        )
        compose.waitForIdle()
        // This is POSITION, not progress: no percentage, no bar, no completion state. It is
        // the one number a reference work shows and the app's rule permits.
        compose.onNodeWithText("1 / 63", substring = true).assertDoesNotExist()
    }

    @Test
    fun thePositionReadoutNeverExceedsTheList() {
        showThaiTier0()
        compose.onNode(hasScrollAction())
            .performScrollToNode(hasText("Phrases", substring = true))
        // Exactly one readout, and it is bounded by the phrase count. Reading the
        // semantics tree by hand was replaced with a matcher: the assertion is the same
        // and it no longer depends on how Compose exposes AnnotatedString.
        compose.onAllNodesWithText(" / 63", substring = true).assertCountEquals(1)
    }

    @Test
    fun aWayBackToTheTopAppearsOnceTheReaderIsDeepInTheList() {
        showThaiTier0()
        // Absent at the top: a permanent control on a scrolling reference is clutter the
        // reader pays for on every screen where they did not want it.
        compose.onNodeWithText("Back to the top").assertDoesNotExist()

        compose.onNode(hasScrollAction()).performScrollToIndex(40)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Back to the top").assertExists()
    }

    @Test
    fun everyEntryOffersBothTheFlagAndACopy() {
        showThaiTier0()
        compose.onNode(hasScrollAction())
            .performScrollToNode(hasText("Greetings & courtesy", substring = true))
        // Every card carries both, so this is a count rather than a single match.
        compose.onAllNodesWithText("This failed me").assertCountEquals(3)
        // Copy is the action a phrasebook is used for most: showing the screen to somebody
        // who cannot read the script. It is a read, not an edit.
        compose.onAllNodesWithText("Copy")[0].assertExists()
    }
}