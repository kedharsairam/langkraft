package com.kedharsairam.langkraft.data

import android.content.Context
import com.kedharsairam.langkraft.domain.DomainResult
import com.kedharsairam.langkraft.domain.ErrorReason
import com.kedharsairam.langkraft.domain.LanguageInfo
import com.kedharsairam.langkraft.domain.NumberEntry
import com.kedharsairam.langkraft.domain.Phrase
import com.kedharsairam.langkraft.domain.SoundCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * One owner of bundled content. Parsing happens on IO exactly once per asset;
 * immutable results are cached in memory for the life of the process.
 */
class ContentRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val phrases = mutableMapOf<String, DomainResult<List<Phrase>>>()
    private val soundChecks = mutableMapOf<String, DomainResult<SoundCheck?>>()
    private val numbers = mutableMapOf<String, DomainResult<List<NumberEntry>>>()
    private var catalogCache: DomainResult<List<LanguageInfo>>? = null

    suspend fun catalog(): DomainResult<List<LanguageInfo>> = withContext(Dispatchers.IO) {
        catalogCache ?: readAsset("langkraft/catalog.json") { text ->
            json.decodeFromString<List<LanguageInfo>>(text)
        }.also { catalogCache = it }
    }

    suspend fun phrases(lang: String): DomainResult<List<Phrase>> = withContext(Dispatchers.IO) {
        mutex.withLock {
            phrases[lang] ?: readJsonl("langkraft/$lang.jsonl") { line ->
                json.decodeFromString<Phrase>(line)
            }.also { phrases[lang] = it }
        }
    }

    suspend fun allPhrases(codes: List<String>): Map<String, List<Phrase>> = withContext(Dispatchers.IO) {
        codes.associateWith { code ->
            (phrases(code) as? DomainResult.Success)?.data ?: emptyList()
        }
    }

    suspend fun soundCheck(lang: String): DomainResult<SoundCheck?> = withContext(Dispatchers.IO) {
        mutex.withLock {
            soundChecks[lang] ?: try {
                DomainResult.Success(
                    json.decodeFromString<SoundCheck>(readText("langkraft/soundcheck-$lang.json"))
                )
            } catch (_: java.io.FileNotFoundException) {
                DomainResult.Success(null)
            } catch (e: Exception) {
                DomainResult.Error(ErrorReason.MalformedJson(e.message ?: "soundcheck"))
            }.also { soundChecks[lang] = it }
        }
    }

    suspend fun numbers(lang: String): DomainResult<List<NumberEntry>> = withContext(Dispatchers.IO) {
        mutex.withLock {
            numbers[lang] ?: readAsset("langkraft/numbers-$lang.json") { text ->
                json.decodeFromString<List<NumberEntry>>(text)
            }.also { numbers[lang] = it }
        }
    }

    private fun readText(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    private inline fun <reified T> readAsset(path: String, decode: (String) -> T): DomainResult<T> = try {
        DomainResult.Success(decode(readText(path)))
    } catch (_: java.io.FileNotFoundException) {
        DomainResult.Error(ErrorReason.MissingAsset)
    } catch (e: Exception) {
        DomainResult.Error(ErrorReason.MalformedJson(e.message ?: path))
    }

    private inline fun <reified T> readJsonl(path: String, noinline decode: (String) -> T): DomainResult<List<T>> = try {
        val rows = readText(path).lineSequence().filter { it.isNotBlank() }.map(decode).toList()
        if (rows.isEmpty()) DomainResult.Error(ErrorReason.Empty(path))
        else DomainResult.Success(rows)
    } catch (_: java.io.FileNotFoundException) {
        DomainResult.Error(ErrorReason.MissingAsset)
    } catch (e: Exception) {
        DomainResult.Error(ErrorReason.MalformedJson(e.message ?: path))
    }
}
