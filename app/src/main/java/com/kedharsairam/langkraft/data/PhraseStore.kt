package com.kedharsairam.langkraft.data

import android.content.Context
import com.kedharsairam.langkraft.domain.LanguageInfo
import com.kedharsairam.langkraft.domain.Phrase
import com.kedharsairam.langkraft.domain.SoundCheck
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

// Reads bundled JSONL from assets/langkraft/<code>.jsonl
class PhraseStore(private val context: Context) {
    fun loadPhrases(lang: String): List<Phrase> {
        val lines = context.assets.open("langkraft/$lang.jsonl").bufferedReader().use { it.readLines() }
        return lines.filter { it.isNotBlank() }.map { json.decodeFromString<Phrase>(it) }
    }
    fun loadCatalog(): List<LanguageInfo> {
        val text = context.assets.open("langkraft/catalog.json").bufferedReader().use { it.readText() }
        return json.decodeFromString<List<LanguageInfo>>(text)
    }
    fun loadSoundCheck(lang: String): SoundCheck? {
        val text = context.assets.open("langkraft/soundcheck-$lang.json").bufferedReader().use { it.readText() }
        return json.decodeFromString(text)
    }
}
