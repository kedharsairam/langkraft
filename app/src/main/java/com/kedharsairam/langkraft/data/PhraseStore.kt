package com.kedharsairam.langkraft.data

import android.content.Context
import com.kedharsairam.langkraft.domain.DomainResult
import com.kedharsairam.langkraft.domain.ErrorReason
import com.kedharsairam.langkraft.domain.LanguageInfo
import com.kedharsairam.langkraft.domain.Phrase
import com.kedharsairam.langkraft.domain.SoundCheck
import kotlinx.serialization.json.Json
import java.io.FileNotFoundException

private val json = Json { ignoreUnknownKeys = true }

// Reads bundled JSONL from assets/langkraft/<code>.jsonl
// Never throws to UI — maps to DomainResult.Error.
class PhraseStore(private val context: Context) {

    fun loadPhrases(lang: String): DomainResult<List<Phrase>> = try {
        val lines = context.assets.open("langkraft/$lang.jsonl").bufferedReader().use { it.readLines() }
        val rows = lines.filter { it.isNotBlank() }.map { json.decodeFromString<Phrase>(it) }
        if (rows.isEmpty()) DomainResult.Error(ErrorReason.Empty("phrases:$lang"))
        else DomainResult.Success(rows)
    } catch (e: FileNotFoundException) {
        DomainResult.Error(ErrorReason.MissingAsset)
    } catch (e: Exception) {
        DomainResult.Error(ErrorReason.MalformedJson(e.message ?: "parse"))
    }

    fun loadCatalog(): DomainResult<List<LanguageInfo>> = try {
        val text = context.assets.open("langkraft/catalog.json").bufferedReader().use { it.readText() }
        DomainResult.Success(json.decodeFromString<List<LanguageInfo>>(text))
    } catch (e: FileNotFoundException) {
        DomainResult.Error(ErrorReason.MissingAsset)
    } catch (e: Exception) {
        DomainResult.Error(ErrorReason.MalformedJson(e.message ?: "catalog"))
    }

    fun loadSoundCheck(lang: String): DomainResult<SoundCheck?> = try {
        val text = context.assets.open("langkraft/soundcheck-$lang.json").bufferedReader().use { it.readText() }
        DomainResult.Success(json.decodeFromString<SoundCheck>(text))
    } catch (e: FileNotFoundException) {
        DomainResult.Success(null)
    } catch (e: Exception) {
        DomainResult.Error(ErrorReason.MalformedJson(e.message ?: "soundcheck"))
    }

    fun loadNumbers(lang: String): DomainResult<List<com.kedharsairam.langkraft.domain.NumberEntry>> = try {
        val text = context.assets.open("langkraft/numbers-$lang.json").bufferedReader().use { it.readText() }
        DomainResult.Success(json.decodeFromString<List<com.kedharsairam.langkraft.domain.NumberEntry>>(text))
    } catch (e: FileNotFoundException) {
        DomainResult.Error(ErrorReason.MissingAsset)
    } catch (e: Exception) {
        DomainResult.Error(ErrorReason.MalformedJson(e.message ?: "numbers"))
    }
}
