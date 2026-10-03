package com.krafttools.langkraft.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Loads the bundled corpus.
 *
 * **Why JSONL and not SQLite.** EnglishKraft uses a prebuilt SQLite database because
 * its corpus is 452 MB of 1.4 million dictionary rows — a case where streaming a text
 * file would be hopeless. LangKraft's corpus is a read-only, authored, shipped-in-APK
 * file of a few thousand records even at full depth. Parsing it once at startup costs
 * well under a second and removes Room, the generated schema, an entire dependency, and
 * a class of migration bug that can only ever hurt a read-only app.
 *
 * If this ever gets large enough that the parse is felt, the migration is to a
 * prebuilt database — and the loader's interface does not need to change.
 *
 * Specs are parsed separately because they are YAML, and YAML is not in the Android
 * runtime. The pipeline emits `assets/specs.json` as a derived artefact; that file is a
 * build output and is gitignored like the other generated ones.
 */
class ContentRepository(private val assets: AssetSource) {

    fun interface AssetSource {
        fun open(name: String): String
    }

    data class Corpus(
        val specs: List<LanguageSpec>,
        val entries: List<Entry>,
        val exchanges: List<Exchange>,
    ) {
        val byLanguage: Map<String, List<Entry>> by lazy {
            entries.groupBy { it.lang }
        }

        fun spec(code: String): LanguageSpec? = specs.firstOrNull { it.code == code }

        fun entriesFor(lang: String, tier: Int): List<Entry> =
            entries.filter { it.lang == lang && it.tier == tier }

        fun exchangesFor(lang: String, tier: Int): List<Exchange> =
            exchanges.filter { it.lang == lang && it.tier == tier }.sortedBy { it.order }

        /** Exchanges containing at least one turn from [entryId]. */
        fun exchangesContaining(entryId: String): List<Exchange> =
            exchanges.filter { ex -> ex.turns.any { it.entryId == entryId } }
    }

    fun load(): Corpus {
        val specs = parseSpecs(assets.open("specs.json"))
        val records = parseRecords(assets.open("content.jsonl"))
        return Corpus(
            specs = specs,
            entries = records.filterIsInstance<Entry>(),
            exchanges = records.filterIsInstance<Exchange>(),
        )
    }

    // ---- specs ------------------------------------------------------------
    private fun parseSpecs(raw: String): List<LanguageSpec> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val lang = o.getJSONObject("language")
            val variety = o.getJSONObject("variety")
            val structure = o.getJSONObject("structure")
            val script = structure.getJSONObject("script")
            val tiersArr = o.getJSONArray("tiers")
            LanguageSpec(
                code = lang.getString("code"),
                name = lang.getString("name"),
                endonym = lang.optString("endonym"),
                romanization = lang.optStringOrNull("romanization"),
                role = lang.optString("role", "course"),
                glossMode = lang.optString("gloss_mode", "required"),
                defaultVariety = variety.optString("default"),
                variants = (0 until (variety.optJSONArray("variants")?.length() ?: 0)).map { v ->
                    val vo = variety.getJSONArray("variants").getJSONObject(v)
                    Variant(id = vo.getString("id"), label = vo.optString("label"))
                },
                scriptPrimary = script.optString("primary"),
                scriptDirection = script.optString("direction", "ltr"),
                registerSystem = o.getJSONObject("register").optString("system", "none"),
                tiers = (0 until tiersArr.length()).map { t ->
                    val to = tiersArr.getJSONObject(t)
                    Tier(
                        id = to.getInt("id"),
                        name = to.optString("name"),
                        intent = to.optString("intent"),
                        // Flattened by emit-assets.mjs. In the spec this is a claim
                        // object {value, source, verified, checked}; the claim wrapper is
                        // build-time metadata and the phone only ever needs the number.
                        // These two shapes disagreeing is the exact drift the loader
                        // tests exist to catch — and it did, on the first run.
                        size = to.optInt("size", 0),
                        certainty = to.optString("certainty"),
                    )
                },
            )
        }
    }

    // ---- content ----------------------------------------------------------
    private fun parseRecords(raw: String): List<Record> =
        raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("//") }
            .map { line ->
                val o = JSONObject(line)
                if (o.has("turns")) parseExchange(o) else parseEntry(o)
            }
            .toList()

    private fun parseEntry(o: JSONObject): Entry = Entry(
        id = o.getString("id"),
        lang = o.getString("lang"),
        tier = o.getInt("tier"),
        domain = o.getInt("domain"),
        textNative = o.getString("text_native"),
        textRomanized = o.optStringOrNull("text_romanized"),
        textEnglish = o.optStringOrNull("text_english"),
        register = o.optString("register", "neutral"),
        direction = Direction.parse(o.getString("direction")),
        why = o.getString("why"),
        exchangeId = o.optStringOrNull("exchange_id"),
        exchangeTurn = if (o.isNull("exchange_turn")) null else o.getInt("exchange_turn"),
        caution = o.optStringOrNull("caution"),
        source = parseSource(o.getJSONObject("source")),
        failureFlags = parseFlags(o.optJSONArray("failure_flags")),
    )

    private fun parseExchange(o: JSONObject): Exchange {
        val turnsArr = o.getJSONArray("turns")
        return Exchange(
            id = o.getString("id"),
            lang = o.getString("lang"),
            tier = o.getInt("tier"),
            domain = o.getInt("domain"),
            scenario = o.getString("scenario"),
            order = o.optInt("order", Int.MAX_VALUE),
            turns = (0 until turnsArr.length()).map { t ->
                val to = turnsArr.getJSONObject(t)
                ExchangeTurn(
                    turn = to.getInt("turn"),
                    speaker = to.getString("speaker"),
                    direction = Direction.parse(to.getString("direction")),
                    entryId = to.optStringOrNull("entry_id"),
                    textNative = to.getString("text_native"),
                    textRomanized = to.optStringOrNull("text_romanized"),
                    textEnglish = to.optStringOrNull("text_english"),
                    optional = to.optBoolean("optional", false),
                )
            },
            source = parseSource(o.getJSONObject("source")),
            failureFlags = parseFlags(o.optJSONArray("failure_flags")),
        )
    }

    private fun parseSource(o: JSONObject) = SourceRef(
        cls = o.getString("class"),
        id = o.getString("id"),
        licence = o.optString("licence"),
    )

    private fun parseFlags(arr: JSONArray?): List<FailureFlag> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            FailureFlag(country = o.optStringOrNull("country"), at = o.optString("at"))
        }
    }

    companion object {
        fun from(context: Context): ContentRepository =
            ContentRepository { name ->
                context.assets.open(name).bufferedReader().use { it.readText() }
            }
    }
}

/**
 * `optString` returns "" for a JSON null, which would silently turn a deliberate
 * `null` into an empty string — and an empty romanisation renders as a blank line
 * where a script should be. Every nullable field in SCHEMA.json goes through here.
 */
private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }