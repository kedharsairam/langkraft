package com.krafttools.langkraft

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.krafttools.langkraft.data.ContentRepository
import com.krafttools.langkraft.ui.LangKraftTheme
import com.krafttools.langkraft.ui.TierScreen
import com.krafttools.langkraft.ui.ToneSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The tone stage, tested against real content rather than a fixture.
 *
 * This capability had a schema, a linter, a UI and a test fixture for two phases before
 * anything ever rendered it, because no tonal language shipped. Thai fixed that. These
 * tests exist so the invariant that makes the stage worth having cannot be broken by a
 * future content pass.
 */
@RunWith(AndroidJUnit4::class)
class ToneStageTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun corpus() = ContentRepository.from(compose.activity).load()

    @Test
    fun thaiShipsToneSets() {
        val sets = corpus().toneSetsFor("tha", 0)
        assertTrue("Thai must ship tone sets, the language was chosen for this", sets.isNotEmpty())
    }

    /**
     * The whole point of the stage: the variants look identical in romanization and differ
     * only in the script. If a future romanization pass starts marking tone here, this
     * fails — and it should, because the collision is the lesson.
     */
    @Test
    fun variantsOfASetShareOneRomanizationAndDifferInScript() {
        for (set in corpus().toneSetsFor("tha", 0)) {
            assertTrue(
                "tone set ${set.id} needs at least two variants to be a contrast",
                set.variants.size >= 2,
            )
            val romanizations = set.variants.mapNotNull { it.textRomanized }.toSet()
            assertEquals(
                "tone set ${set.id} should collapse to ONE romanization, got $romanizations",
                1,
                romanizations.size,
            )
            val scripts = set.variants.map { it.textNative }.toSet()
            assertEquals(
                "tone set ${set.id} variants must be visually distinct, got $scripts",
                set.variants.size,
                scripts.size,
            )
            assertTrue(
                "tone set ${set.id} must carry at least two distinct tone numbers",
                set.variants.map { it.tone }.toSet().size >= 2,
            )
        }
    }

    @Test
    fun everyVariantSaysWhatItMeans() {
        // A tone number with no meaning teaches a squiggle rather than a contrast. The
        // content linter enforces this too; the test is here so a pipeline bypass shows
        // up as a failing test rather than as content nobody looks at again.
        for (set in corpus().toneSetsFor("tha", 0)) {
            for (v in set.variants) {
                assertTrue("${set.id} tone ${v.tone} has no meaning", v.textEnglish.isNotBlank())
            }
        }
    }

    @Test
    fun toneSetsAreScopedToTheirTier() {
        // They are tone 0 content. Rendering them above every tier would put the whole
        // tone section in front of Tier 2's "Nothing authored at this tier yet".
        val c = corpus()
        assertTrue(c.toneSetsFor("tha", 0).isNotEmpty())
        assertTrue(c.toneSetsFor("tha", 1).isEmpty())
    }

    @Test
    fun aLanguageWithoutTonesHasNone() {
        val c = corpus()
        for (code in listOf("eng", "swh", "tam")) {
            assertTrue(
                "$code declares tones: false and must not carry tone sets",
                c.toneSetsFor(code, 0).isEmpty(),
            )
        }
    }

    @Test
    fun theToneSectionRenders() {
        val c = corpus()
        val spec = c.spec("tha")!!
        compose.setContent {
            LangKraftTheme {
                ToneSection(
                    sets = remember(c) { c.toneSetsFor("tha", 0) },
                    spec = spec,
                    onFlag = { },
                )
            }
        }
        compose.onNodeWithText("Tones").assertIsDisplayed()
        compose.onNodeWithText("cooking stove, oven").assertIsDisplayed()
    }

    @Test
    fun toneSetsRenderBeforePhrasesInTheTierScreen() {
        val c = corpus()
        val spec = c.spec("tha")!!
        compose.setContent {
            LangKraftTheme {
                TierScreen(spec = spec, tier = 0, corpus = c, onBack = { })
            }
        }
        compose.waitForIdle()
        // A learner who cannot see that the marks differ will misread every phrase after.
        compose.onNodeWithText("Tones").assertIsDisplayed()
        compose.onNodeWithText("These marks change what every other word means.")
            .assertIsDisplayed()
    }

    @Test
    fun thaiCarriesNoGenderedPronounInAnyPhrase() {
        // ผม and ดิฉัน hardcode the speaker's gender, and the app cannot know the user's
        // gender and is forbidden from asking. A gendered pronoun in shipped content would
        // either misgender the learner or make the phrase unusable.
        val c = corpus()
        val banned = listOf("ผม", "ดิฉัน", "ฉัน")
        for (e in c.entriesFor("tha", 0)) {
            for (b in banned) {
                assertTrue(
                    "tha entry ${e.id} contains the gendered first-person '$b'",
                    !e.textNative.contains(b),
                )
            }
        }
    }

    @Test
    fun thaiRegistersGenderOnlyInTheParticlesThemselves() {
        // ครับ and ค่ะ are legitimately gendered — that is what they ARE, and the spec
        // records that the user must pick. The rule is that nothing ELSE hardcodes gender,
        // and that the two particles are never scripted into a dialogue turn.
        val c = corpus()
        val particles = c.entriesFor("tha", 0)
            .filter { it.textNative == "ครับ" || it.textNative == "ค่ะ" }
            .map { it.id }
            .toSet()
        assertTrue("both polite particles must be authored", particles.size == 2)
        for (ex in c.exchangesFor("tha", 0)) {
            for (t in ex.turns) {
                assertNotNull(t.textNative)
                assertTrue(
                    "exchange ${ex.id} turn ${t.turn} scripts the gendered particle " +
                        "'${t.textNative}'; the learner has to choose, not be shown one",
                    !t.textNative.endsWith("ครับ") && !t.textNative.endsWith("ค่ะ"),
                )
            }
        }
    }
}