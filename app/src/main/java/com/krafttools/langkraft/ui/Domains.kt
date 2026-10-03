package com.krafttools.langkraft.ui

/**
 * The twelve domains.
 *
 * These are invariant across every language. What varies per language is how many
 * items sit inside each one, which lives in the content rather than here.
 *
 * The numbers are the contract from `content/SCHEMA.json` — an item's `domain` is an
 * integer in 1..12 and the linter enforces the range. The map is the human-facing name.
 * A domain with no entry here would render as a bare number, which is the kind of thing
 * that only gets noticed in a screenshot.
 */
enum class Domain(val number: Int, val label: String) {
    GREETINGS(1, "Greetings & courtesy"),
    NUMBERS_TIME(2, "Numbers & time"),
    MONEY(3, "Money & prices"),
    FOOD(4, "Food & drink"),
    ACCOMMODATION(5, "Somewhere to stay"),
    TRANSPORT(6, "Getting around"),
    DIRECTIONS(7, "Where things are"),
    SHOPPING(8, "Shopping"),
    HEALTH(9, "Health"),
    EMERGENCIES(10, "Emergencies"),
    PEOPLE(11, "People"),
    REPAIR(12, "Repair & survival");

    companion object {
        private val byNumber = entries.associateBy { it.number }

        /** Falls back to a neutral label rather than throwing: a missing domain must not crash a reader. */
        fun of(number: Int): Domain = byNumber[number] ?: REPAIR

        /**
         * Tier 0 is courtesy, so it is deliberately built from domains 1 and 12 only.
         * Repair matters more than it looks: it is how a learner stays inside an
         * interaction instead of falling out of it and into English.
         */
        fun tier0Domains(): List<Domain> = listOf(GREETINGS, REPAIR)
    }
}

/** The four tiers. Ids and order are invariant; sizes vary per language. */
enum class TierId(val number: Int, val title: String, val blurb: String) {
    T0(0, "Courtesy", "Not rude."),
    T1(1, "Transaction", "It works."),
    T2(2, "Independence", "Unaided."),
    T3(3, "Conversation", "Small talk.");

    companion object {
        fun of(number: Int): TierId = entries.firstOrNull { it.number == number } ?: T0
    }
}