package com.krafttools.langkraft.data

/**
 * Search over the corpus.
 *
 * Built once at startup, held in memory. At full depth the catalogue is roughly 24,000
 * entries and 20 languages; three parallel lowercase string lists is a few megabytes and
 * a linear scan per keystroke, which is far below the threshold where anything cleverer
 * would pay for itself.
 *
 * **Three fields are searched, and that is the whole design.** A learner standing in
 * Zanzibar knows the phrase they want in one of three ways: they saw it in a film
 * (romanization), they read it on a sign (native script), or they know what it MEANS and
 * not how to say it (English). Searching only the native script fails for a learner who
 * cannot read the script yet, which is most of them, early. Searching only the English
 * fails for someone who has seen the word and does not know its meaning. All three, or
 * search is not a feature.
 *
 * Ranking is deliberately crude and in the right order:
 *   1. native script matches by prefix  — you know the shape of the word
 *   2. romanization matches by prefix   — you know how it sounds
 *   3. english matches by prefix        — you know what it means
 *   4. anything matches as a substring  — the fallback, ranked last
 *
 * Exact matches outrank prefixes outrank substrings within a field, because "what does
 * *this* word mean" and "something with water in it" are different questions.
 */
class SearchIndex(private val corpus: ContentRepository.Corpus) {

    data class Hit(
        val entry: Entry,
        /** Lower is better. Only meaningful within a single query. */
        val rank: Int,
        /** Which field matched — shown in the result so the answer is legible. */
        val matchedOn: Field,
    )

    enum class Field(val label: String) {
        SCRIPT("script"), ROMANIZATION("romanization"), ENGLISH("meaning"), SCENARIO("scenario")
    }

    private data class Indexed(
        val entry: Entry,
        val script: String,
        // Nullable rather than a sentinel string. An absent romanization used to be
        // replaced with a raw NUL byte as an "unmatchable" marker, which made this file
        // binary to git — no diffs, no merges, no review, permanently. A null says the
        // same thing with no bytes involved.
        val romanization: String?,
        val english: String?,
        val scenario: String?,
    )

    private val items: List<Indexed> by lazy {
        val scenarios = corpus.exchanges.associate { it.id to it.scenario.lowercase() }
        corpus.entries.map { e ->
            Indexed(
                entry = e,
                script = e.textNative.lowercase(),
                // Lowercased, and left null when absent. `""` would be the bug: every
                // absent field would then match every query.
                romanization = e.textRomanized?.lowercase(),
                english = e.textEnglish?.lowercase(),
                scenario = scenarios[e.exchangeId]?.lowercase(),
            )
        }
    }

    /**
     * @param limit upper bound on results. The UI shows a short list; a caller wanting
     *              everything should page rather than ask for 24,000 rows at once.
     */
    fun search(query: String, lang: String? = null, limit: Int = 40): List<Hit> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<Hit>(limit)

        for (item in items) {
            if (lang != null && item.entry.lang != lang) continue

            // Exact, then prefix, then substring — per field, in field priority order.
            val scored: Int
            val on: Field
            when {
                item.script == q -> { scored = 0; on = Field.SCRIPT }
                item.romanization == q -> { scored = 1; on = Field.ROMANIZATION }
                item.english == q -> { scored = 2; on = Field.ENGLISH }
                item.script.startsWith(q) -> { scored = 3; on = Field.SCRIPT }
                item.romanization?.startsWith(q) == true -> { scored = 4; on = Field.ROMANIZATION }
                item.english?.startsWith(q) == true -> { scored = 5; on = Field.ENGLISH }
                item.scenario?.startsWith(q) == true -> { scored = 6; on = Field.SCENARIO }
                item.script.contains(q) -> { scored = 7; on = Field.SCRIPT }
                item.romanization?.contains(q) == true -> { scored = 8; on = Field.ROMANIZATION }
                item.english?.contains(q) == true -> { scored = 9; on = Field.ENGLISH }
                item.scenario?.contains(q) == true -> { scored = 10; on = Field.SCENARIO }
                else -> continue
            }
            out.add(Hit(item.entry, scored, on))
            if (out.size >= limit * 4) break
        }

        return out.sortedWith(compareBy({ it.rank }, { it.entry.id })).take(limit)
    }
}
