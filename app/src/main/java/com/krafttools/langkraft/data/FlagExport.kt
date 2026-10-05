package com.krafttools.langkraft.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Writes the failure flags out as JSON, so the one thing a reader contributes can actually
 * be used.
 *
 * **Why this exists.** The "this failed me" flag is the app's only permitted input, and its
 * entire purpose is to correct the SPECS from lived experience — a tier size guessed
 * without ever standing in the country, revised by someone who did. But `flagsFor` was
 * called from tests and nowhere else: the rows were written and then went nowhere. The
 * loop the product is built around was open at both ends.
 *
 * **Why SAF and not a file path.** The app has zero permissions, by rule and by manifest.
 * Writing to shared storage needs one. `ACTION_CREATE_DOCUMENT` needs none: the reader
 * chooses the destination through the system picker and the app is handed a one-shot URI.
 * That is the only way to write a file the reader can keep without asking for anything.
 *
 * **What is exported and what is not.** Phrase text, the reader's country, the timestamp,
 * and the tier. No position, no scroll history, no identifier that could identify a device,
 * and nothing that was not already in the database the reader created. It is their data and
 * it goes where they put it.
 *
 * Per-sentence attribution matters here: Tatoeba content is CC BY 2.0 FR and names an author
 * per sentence. Anything derived from it therefore carries the entry's provenance into the
 * export, so a reviewer receiving this file can see what came from where.
 */
object FlagExport {

    data class Row(
        val language: String,
        val languageName: String,
        val tier: Int,
        val entryId: String?,
        val textNative: String?,
        val textEnglish: String?,
        val country: String?,
        val at: String,
        val sourceId: String?,
        val sourceLicence: String?,
    )

    /**
     * Builds the export document.
     *
     * @param flags grouped by language, most recent first, straight from `flagsFor`.
     */
    fun buildDocument(
        corpus: ContentRepository.Corpus,
        flags: Map<String, List<FailureFlag>>,
    ): String {
        // entry_id is namespaced "entry:tha-0012" / "exchange:tha-x0004" / "tone:tha-t0001".
        // Resolve it back to the record so the reviewer sees the phrase, not our internal
        // key. An unresolvable id is emitted as-is rather than dropped: a dangling
        // reference is information, and silently losing rows would understate the problem.
        val byId = corpus.entries.associateBy { it.id }
        val exchanges = corpus.exchanges.associateBy { it.id }
        val toneSets = corpus.toneSets.associateBy { it.id }

        val out = JSONArray()
        for ((lang, rows) in flags) {
            val spec = corpus.spec(lang)
            for (f in rows) {
                val bare = f.entryId.substringAfter(':', f.entryId)
                val entry = byId[bare]
                val exchange = exchanges[bare]
                val tone = toneSets[bare]
                out.put(
                    JSONObject().apply {
                        put("language", lang)
                        put("language_name", spec?.name ?: lang)
                        put("kind", f.entryId.substringBefore(':', "entry"))
                        put("entry_id", bare)
                        put("tier", entry?.tier ?: exchange?.tier ?: tone?.tier ?: 0)
                        put("text_native", entry?.textNative ?: exchange?.scenario ?: tone?.syllable)
                        put("text_romanized", entry?.textRomanized)
                        put("text_english", entry?.textEnglish ?: tone?.variants?.joinToString(" / ") { it.textEnglish })
                        put("country", f.country ?: JSONObject.NULL)
                        put("at", f.at)
                        // Provenance travels with the row. A reviewer who receives this file
                        // is about to act on the content, and CC BY 2.0 FR names an author
                        // per sentence.
                        put("source_id", entry?.source?.id ?: JSONObject.NULL)
                        put("source_licence", entry?.source?.licence ?: JSONObject.NULL)
                    }
                )
            }
        }

        return JSONObject().apply {
            put("format", "langkraft-failure-flags")
            put("version", 1)
            put(
                "note",
                "Phrases the reader flagged as having failed them in real use, with the " +
                    "country where it happened. Collected offline on the reader's own " +
                    "device. Intended for revising the language specs.",
            )
            put("languages", corpus.specs.size)
            put("flag_count", out.length())
            put("flags", out)
        }.toString(2)
    }

    /**
     * Writes the document through a URI the reader chose.
     *
     * @return bytes written, or -1 if the write failed.
     */
    fun write(context: Context, uri: Uri, document: String): Int = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(document.toByteArray(Charsets.UTF_8))
            out.flush()
            out.toString().length
        } ?: -1
    }.getOrElse { -1 }
}