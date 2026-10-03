package com.krafttools.langkraft.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Loader tests, run against the real authored corpus rather than a fixture.
 *
 * A hand-written fixture proves the parser handles what the author imagined. Reading
 * `content/eng-tier0.jsonl` proves it handles what was actually written — which is
 * where the schema and the real content drift apart, and drift is the thing that ships
 * a wrong floor to the learner.
 */
class ContentRepositoryTest {

    private val repo = File("..")

    private fun corpusFrom(contentJsonl: String, specsJson: String): ContentRepository.Corpus =
        ContentRepository { name -> if (name == "content.jsonl") contentJsonl else specsJson }
            .load()

    private val realContent: String get() = File(repo, "content/eng-tier0.jsonl").readText()
    private val realSpecs: String get() = File(repo, "app/src/main/assets/specs.json").readText()

    // ---- the real corpus --------------------------------------------------
    @Test
    fun `parses the real corpus without error`() {
        val corpus = corpusFrom(realContent, realSpecs)
        assertEquals("every authored record should parse", 48, corpus.entries.size)
        assertEquals(12, corpus.exchanges.size)
    }

    @Test
    fun `every entry has a why`() {
        corpusFrom(realContent, realSpecs).entries.forEach {
            assertTrue("entry ${it.id} has no why", it.why.isNotBlank())
        }
    }

    @Test
    fun `english is latin so no entry carries a romanisation`() {
        val corpus = corpusFrom(realContent, realSpecs)
        assertTrue(corpus.spec("eng")!!.isLatinScript)
        corpus.entries.forEach {
            assertNull("latin-script entry ${it.id} should have no romanisation", it.textRomanized)
        }
    }

    @Test
    fun `english has gloss_mode same_as_native so no gloss is carried`() {
        val corpus = corpusFrom(realContent, realSpecs)
        assertTrue(corpus.spec("eng")!!.glossIsNative)
        corpus.entries.forEach {
            assertNull("entry ${it.id} should carry no gloss", it.textEnglish)
        }
    }

    @Test
    fun `every exchange contains a turn the learner says`() {
        // One exchange is deliberately receptive-only, so it is the exception, not the rule.
        corpusFrom(realContent, realSpecs).exchanges.forEach { ex ->
            if (ex.turns.none { it.isYou }) {
                assertTrue(
                    "receptive-only exchange ${ex.id} should be flagged, not accidental",
                    ex.turns.size >= 3,
                )
            }
        }
    }

    @Test
    fun `no them-turn links to a production entry`() {
        // The rule that keeps production and reception from being conflated.
        corpusFrom(realContent, realSpecs).exchanges.forEach { ex ->
            ex.turns.filterNot { it.isYou }.forEach { turn ->
                assertNull("${ex.id} turn ${turn.turn} links a them-turn to an entry", turn.entryId)
            }
        }
    }

    @Test
    fun `entry ids are unique and never reused`() {
        val corpus = corpusFrom(realContent, realSpecs)
        val ids = (corpus.entries.map { it.id } + corpus.exchanges.map { it.id })
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `direction balance favours production across entries and turns`() {
        // The research says production is the hard part and should outnumber
        // recognition. Counting entries alone undercounts reception badly, because most
        // of it lives on the `them` side of exchanges.
        val corpus = corpusFrom(realContent, realSpecs)
        val entriesSay = corpus.entries.count { it.direction == Direction.SAY }
        val entriesUnderstand = corpus.entries.count { it.direction == Direction.UNDERSTAND }
        val turnsSay = corpus.exchanges.sumOf { ex -> ex.turns.count { it.direction == Direction.SAY } }
        val turnsUnderstand = corpus.exchanges.sumOf { ex -> ex.turns.count { it.direction == Direction.UNDERSTAND } }

        assertTrue(
            "production should outnumber reception overall",
            (entriesSay + turnsSay) > (entriesUnderstand + turnsUnderstand),
        )
        assertTrue(
            "counting entries alone undercounts reception, so the test must use both",
            turnsUnderstand > 0,
        )
    }

    // ---- parsing behaviour ------------------------------------------------
    @Test
    fun `a null optional field becomes null and not an empty string`() {
        // org.json's optString returns "" for a JSON null. If this regresses, a blank
        // romanisation renders as an empty line where a script should be.
        // One record per line — JSONL, not pretty-printed JSON.
        val one = """{"id":"x1","lang":"tam","tier":0,"domain":1,"text_native":"வணக்கம்","text_romanized":null,"text_english":null,"register":"neutral","direction":"say","why":"w","exchange_id":null,"exchange_turn":null,"caution":null,"source":{"class":"authored","id":"x","licence":"y"},"failure_flags":[]}"""
        val corpus = corpusFrom(one, realSpecs)
        assertNull(corpus.entries.single().textRomanized)
        assertNull(corpus.entries.single().caution)
    }

    @Test
    fun `an invalid direction fails loudly rather than defaulting`() {
        val bad = """{"id":"x1","lang":"tam","tier":0,"domain":1,"text_native":"a","text_romanized":"a","text_english":"a","register":"neutral","direction":"maybe","why":"w","source":{"class":"authored","id":"x","licence":"y"},"failure_flags":[]}"""
        val thrown = runCatching { corpusFrom(bad, realSpecs) }.exceptionOrNull()
        assertNotNull("an invalid direction must throw, not default", thrown)
    }

    @Test
    fun `comment and blank lines in jsonl are skipped`() {
        val withNoise = "\n// a comment\n" +
            """{"id":"x1","lang":"tam","tier":0,"domain":1,"text_native":"a","text_romanized":"a","text_english":"a","register":"neutral","direction":"say","why":"w","source":{"class":"authored","id":"x","licence":"y"},"failure_flags":[]}""" +
            "\n\n"
        assertEquals(1, corpusFrom(withNoise, realSpecs).entries.size)
    }

    @Test
    fun `a record split across lines is rejected rather than half-parsed`() {
        // JSONL is one record per line. A pretty-printed object spanning lines must fail
        // loudly; silently parsing the first fragment would drop content without notice.
        val broken = """{"id":"x1","lang":"tam","tier":0,"domain":1,
            "text_native":"a","direction":"say"}"""
        val thrown = runCatching { corpusFrom(broken, realSpecs) }.exceptionOrNull()
        assertNotNull("a multi-line record must not parse", thrown)
    }

    @Test
    fun `an unknown asset name fails loudly`() {
        val thrown = runCatching {
            ContentRepository { throw IllegalArgumentException("unknown asset nope") }.load()
        }.exceptionOrNull()
        assertTrue(thrown is IllegalArgumentException)
    }
}