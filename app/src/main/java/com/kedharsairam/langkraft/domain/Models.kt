package com.kedharsairam.langkraft.domain

import kotlinx.serialization.Serializable

@Serializable
data class Phrase(
    val id: String,
    val lang: String,
    val theme: Int,
    val english: String,
    val romanization: String,
    val youSay: String,
    val soundTip: String,
    val note: String,
    val source: String = "curated",
    val checked: String = "2026-11-06"
)

@Serializable
data class LanguageInfo(
    val code: String,
    val name: String,
    val status: String, // committed | bench
    val kitTotal: Int = 40
)

@Serializable
data class SoundCheck(
    val lang: String,
    val newSounds: String,
    val rhythm: String,
    val registerFirst: String,
    val trap: String
)

@Serializable
data class NumberEntry(
    val value: String,
    val word: String,
    val youSay: String,
    val note: String = "",
    val source: String = "curated",
    val checked: String = "2026-11-06"
)

// Pure domain helpers — no Android imports, fully unit-testable.
fun nextIndex(current: Int, total: Int): Int = if (total <= 0) 0 else (current + 1) % total
fun prevIndex(current: Int, total: Int): Int = if (total <= 0) 0 else (current - 1 + total) % total

fun progressLabel(done: Int, total: Int): String = "$done/$total"

// Style guide enforcement for "You say: " — stress must be in CAPS, at least one capital chunk.
fun isValidYouSay(s: String): Boolean {
    val trimmed = s.trim()
    if (trimmed.length < 2) return false
    // Must contain at least one uppercase letter (stress marker)
    return trimmed.any { it.isUpperCase() }
}
