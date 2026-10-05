package com.krafttools.langkraft.data

/**
 * Records exactly as `content/SCHEMA.json` defines them.
 *
 * The pipeline is the gate: nothing reaches the app that has not passed
 * `content/lint.mjs`, and since 2026-10-04 the Gradle build actually runs those linters
 * rather than only emitting assets.
 *
 * **But this file is not uniformly strict, and an earlier version of this comment said
 * it was, which was false in a way that mattered.** Roughly a third of the fields fall
 * back to a default via `optString`. That is only defensible where the fallback is the
 * SAFE side of a judgement:
 *
 *  - `script.primary` -> "Latin". The predicate is `"".contains("Latin")`, so an empty
 *    fallback reads as NON-Latin and picks a serif face for Latin text. The default has
 *    to be Latin or the failure lands on the wrong language.
 *  - `certainty` -> "low". The UI tests `== "low"`, so an empty fallback would erase the
 *    "Unproven estimate" label and present a hypothesis as fact.
 *
 * Where no fallback is safe, parsing throws. `script.direction` is the example: guessing
 * "ltr" would silently render a right-to-left language left-to-right, so a missing or
 * invalid value refuses to open the app instead.
 *
 * The rule for adding a field: default it only if the wrong answer is harmless, and say
 * in a comment which way the default errs.
 */

enum class Direction { SAY, UNDERSTAND;

    companion object {
        fun parse(raw: String): Direction = when (raw) {
            "say" -> SAY
            "understand" -> UNDERSTAND
            else -> throw IllegalArgumentException(
                "direction must be 'say' or 'understand', was '$raw'. The pipeline should have rejected this."
            )
        }
    }
}

/** Where an item came from. `cls` is one of authored | curated | corpus. */
data class SourceRef(
    val cls: String,
    val id: String,
    val licence: String,
)

data class FailureFlag(
    val entryId: String,
    val country: String?,
    val at: String,
)

data class Entry(
    val id: String,
    override val lang: String,
    override val tier: Int,
    override val domain: Int,
    val textNative: String,
    /** Null for Latin-script languages. Never a duplicate of [textNative]. */
    val textRomanized: String?,
    /** Null only when the spec sets `gloss_mode: same_as_native`. */
    val textEnglish: String?,
    val register: String,
    val direction: Direction,
    val why: String,
    val exchangeId: String?,
    val exchangeTurn: Int?,
    val caution: String?,
    override val source: SourceRef,
    override val failureFlags: List<FailureFlag>,
) : Record {
    override val recordId get() = id

    /**
     * What "Copy" puts on the clipboard.
     *
     * All three lines, because the reader usually wants to SHOW the phrase and does not
     * know which line the person in front of them can read. Native leads: it is the
     * language itself rather than a description of it.
     *
     * The romanization is included only when it differs from the native text. For a Latin
     * language the two are the same string by definition, and printing it twice would make
     * the app look as though it had not noticed.
     */
    fun copyText(): String = buildString {
        append(textNative)
        textRomanized
            ?.takeIf { it.isNotBlank() && it != textNative }
            ?.let { append("  ·  ").append(it) }
        textEnglish
            ?.takeIf { it.isNotBlank() && it != textNative }
            ?.let { append("  ·  ").append(it) }
    }
}

data class ExchangeTurn(
    val turn: Int,
    val speaker: String,
    val direction: Direction,
    val entryId: String?,
    val textNative: String,
    val textRomanized: String?,
    val textEnglish: String?,
    val optional: Boolean,
) {
    val isYou: Boolean get() = speaker == "you"
}

data class Exchange(
    val id: String,
    override val lang: String,
    override val tier: Int,
    override val domain: Int,
    val scenario: String,
    /** Curated display order. Alphabetical is arbitrary and tells a reader nothing. */
    val order: Int,
    val turns: List<ExchangeTurn>,
    override val source: SourceRef,
    override val failureFlags: List<FailureFlag>,
) : Record {
    override val recordId get() = id
}

data class ToneVariant(
    val tone: Int,
    val toneName: String?,
    val textNative: String,
    /** Carries an explicit tone number for a tonal language, e.g. `maa3`. */
    val textRomanized: String?,
    val textEnglish: String,
    val textNote: String?,
)

/**
 * A minimal contrast set: one syllable written several ways, differing only by tone.
 *
 * This is the only tone content a no-audio app can honestly ship. The app cannot teach the
 * SOUND of a tone, and pretending otherwise with a tone-number table would be decoration.
 * What it CAN do is teach the orthographic reality — that the mark is the only difference
 * between two words a learner would otherwise read identically — and that is a real and
 * necessary half. The other half is external, which is where listening already lives.
 */
data class ToneSet(
    val id: String,
    override val lang: String,
    override val tier: Int,
    val syllable: String?,
    val variants: List<ToneVariant>,
    override val source: SourceRef,
    override val failureFlags: List<FailureFlag>,
) : Record {
    override val recordId get() = id
    override val domain get() = 0
}

sealed interface Record {
    val recordId: String
    val lang: String
    val tier: Int
    val domain: Int
    val source: SourceRef
    val failureFlags: List<FailureFlag>
}

/** One of the four tiers. Sizes vary per language; ids and order never do. */
data class Tier(
    val id: Int,
    val name: String,
    val intent: String,
    val size: Int,
    val certainty: String,
)

/**
 * The per-language spec, trimmed to what the app needs at runtime.
 *
 * Only the fields the app actually reads are carried over. Provenance and the review
 * block stay in the YAML in the repository, for the build and for review.
 *
 * `attribution` is the exception: it IS carried, because a licence obligation is not
 * documentation. CC BY-SA content was being redistributed with nowhere in the app to
 * credit it, and bundling two OFL font families added a second obligation with no place
 * to discharge it either.
 */
/** One licence the app must discharge. Lives in `data` because the loader parses it. */
data class Credit(
    val source: String,
    val licence: String,
    val authorCredit: String?,
    val note: String? = null,
)

/** Per-language attribution, carried from the spec so a new language brings its own. */
data class Attribution(
    val source: String,
    val licence: String,
    val authorCredit: String?,
)

data class LanguageSpec(
    val code: String,
    val name: String,
    val endonym: String,
    val romanization: String?,
    val role: String,
    val glossMode: String,
    val defaultVariety: String,
    val variants: List<Variant>,
    val scriptPrimary: String,
    val scriptDirection: String,
    val registerSystem: String,
    /**
     * True when the polite form marks the SPEAKER's gender and there is no verified
     * neutral alternative. Thai only, so far.
     *
     * This cannot be resolved by the app. It cannot know the reader's gender, and the
     * product rules forbid asking, so the particle is a choice the reader makes rather
     * than a default the app picks.
     */
    val registerGenderMarked: Boolean = false,
    val attribution: List<Attribution> = emptyList(),
    val tiers: List<Tier>,
) {
    val isLatinScript: Boolean get() = scriptPrimary.contains("Latin", ignoreCase = true)

    /** True when the gloss IS the native text, so the renderer shows one line. */
    val glossIsNative: Boolean get() = glossMode == "same_as_native"
}

data class Variant(
    val id: String,
    val label: String,
    /** Plain-language guidance. Null when the spec declares none. */
    val note: String? = null,
)