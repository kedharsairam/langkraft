package com.krafttools.langkraft.data

import android.content.ContentValues
import android.util.Log
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteOpenHelper

/**
 * The app's only mutable state.
 *
 * Two things live here and nothing else:
 *
 *  1. **Where you are.** `position` — one row per language per tier. This is a bookmark,
 *     not a score. There is deliberately no accuracy column, no time column, and no
 *     counter anywhere in this file, because the product rule is that progress means
 *     position only. A schema with a `correct` column would make it easy to add a streak
 *     later by accident.
 *
 *  2. **What failed you.** `failure_flag` — the one input the app is ever allowed to take.
 *     It exists so specs can be corrected from lived experience rather than from research
 *     alone, which is the only way a floor estimated without ever standing in the country
 *     can ever become right.
 *
 * **Separate from the corpus, deliberately.** The content ships in `assets/` and is never
 * written to. This database lives in the app's private storage. Rebuilding or upgrading
 * the corpus therefore cannot cost the learner their position or their flags — the same
 * separation EnglishKraft uses for `dictionary.db` and `progress.db`, and the same reason.
 *
 * **No permissions.** App-private storage needs none. Nothing here is readable by another
 * app, and nothing is written off-device.
 */
class ProgressStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DB_NAME,
    null,
    DB_VERSION,
) {
    /** Retained only so a corrupt file can be deleted and rebuilt. */
    private val appContext: Context = context.applicationContext


    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_POSITION (
                lang        TEXT    NOT NULL,
                tier        INTEGER NOT NULL,
                item_index  INTEGER NOT NULL,
                updated_at  INTEGER NOT NULL,
                PRIMARY KEY (lang, tier)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_FLAG (
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                lang       TEXT    NOT NULL,
                entry_id   TEXT    NOT NULL,
                country    TEXT,
                at         INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // Flags are queried by language to revise a spec, so index the language and
        // the entry. NOT NULL on lang and entry_id is the point: a flag with neither is
        // a bug, and the spec review cannot use it.
        db.execSQL("CREATE INDEX idx_flag_lang ON $TABLE_FLAG (lang)")
        db.execSQL("CREATE INDEX idx_flag_entry ON $TABLE_FLAG (entry_id)")
    }

    /**
     * A downgrade must not brick the app.
     *
     * Without this, `SQLiteOpenHelper` throws `Can't downgrade database` on open, before
     * any of our code runs, so there is no way to clear it from inside the app and every
     * launch dies the same way. Side-loading an older build is a normal thing to do while
     * developing, and a phrasebook that cannot be opened is the worst failure this app
     * has. The data is a bookmark and a list of complaints; recreating it is acceptable.
     */
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Nothing here is derived and nothing is expensive to rebuild: position is a
        // bookmark and flags are complaints. Recreating both is safer than a migration
        // path nobody has exercised, and losing a bookmark costs one tap.
        db.execSQL("DROP TABLE IF EXISTS $TABLE_POSITION")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FLAG")
        onCreate(db)
    }

    /**
     * Runs a database call, recovering once from a corrupt file.
     *
     * The first touch of this store is `allPositions()` during composition of the home
     * screen, so an unreadable `progress.db` used to kill the process before anything
     * rendered -- and recurred on every launch, because nothing ever deleted the file.
     * The content is read-only and unaffected, so the right behaviour is to lose the
     * bookmark rather than the phrasebook.
     *
     * Only corruption is recovered. A genuine I/O failure propagates, because silently
     * swallowing those would hide a real disk problem behind a bookmark that never saves.
     */
    private fun <T> orRecreate(block: () -> T): T = try {
        block()
    } catch (e: SQLiteDatabaseCorruptException) {
        Log.w(TAG, "progress.db unreadable; recreating. Bookmark lost, content intact.", e)
        appContext.deleteDatabase(DB_NAME)
        block()
    }

    // ---- position ---------------------------------------------------------

    fun position(lang: String, tier: Int): Int =
        orRecreate { readableDatabase.query(
            TABLE_POSITION, arrayOf("item_index"), "lang = ? AND tier = ?",
            arrayOf(lang, tier.toString()), null, null, null,
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 } }

    /** Records where the learner is. Called on leaving a tier, never on a timer. */
    fun setPosition(lang: String, tier: Int, itemIndex: Int) = orWrite(Unit) {
        val cv = ContentValues().apply {
            put("lang", lang)
            put("tier", tier)
            put("item_index", itemIndex)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict(
            TABLE_POSITION, null, cv, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    /** Every stored position, for the home screen. Languages never opened are absent. */
    fun allPositions(): Map<String, List<Position>> = orRecreate {
        val out = mutableMapOf<String, MutableList<Position>>()
        readableDatabase.query(
            TABLE_POSITION, arrayOf("lang", "tier", "item_index", "updated_at"),
            null, null, null, null, null,
        ).use { c ->
            while (c.moveToNext()) {
                out.getOrPut(c.getString(0)) { mutableListOf() } += Position(
                    tier = c.getInt(1),
                    itemIndex = c.getInt(2),
                    updatedAt = c.getLong(3),
                )
            }
        }
        out
    }

    /** One stored bookmark. [updatedAt] is what "which tier was I last in" actually needs. */
    data class Position(
        val tier: Int,
        val itemIndex: Int,
        val updatedAt: Long,
    )

    fun clearPosition(lang: String) {
        writableDatabase.delete(TABLE_POSITION, "lang = ?", arrayOf(lang))
    }

    // ---- failure flags ----------------------------------------------------

    /**
     * The one input the app ever takes. One row per tap; tapping again is allowed and
     * records a second observation, because "this failed me twice in Morocco" is real
     * information and de-duplicating it would throw the strongest signal away.
     */
    fun addFlag(lang: String, entryId: String, country: String?): Boolean = orWrite(false) {
        val cv = ContentValues().apply {
            put("lang", lang)
            put("entry_id", entryId)
            put("country", country)
            put("at", System.currentTimeMillis())
        }
        writableDatabase.insert(TABLE_FLAG, null, cv) != -1L
    }

    fun flagsFor(lang: String): List<FailureFlag> = orRecreate {
        val out = mutableListOf<FailureFlag>()
        readableDatabase.query(
            TABLE_FLAG, arrayOf("entry_id", "country", "at"),
            "lang = ?", arrayOf(lang), null, null, "at DESC",
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    FailureFlag(
                        country = c.getString(1),
                        at = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                            .format(java.util.Date(c.getLong(2))),
                        entryId = c.getString(0),
                    )
                )
            }
        }
        out
    }

    fun flagCount(lang: String): Int = orRecreate {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_FLAG WHERE lang = ?", arrayOf(lang)
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    /** Clears a language's flags. Used by the spec-review tool, never by the UI. */
    fun clearFlags(lang: String) {
        writableDatabase.delete(TABLE_FLAG, "lang = ?", arrayOf(lang))
    }

    /**
     * Runs a write, reporting failure instead of propagating it.
     *
     * Called from the scroll-position collector and from tap handlers, both on the main
     * dispatcher. A `SQLiteDiskFullException` there was an uncaught exception, which means
     * a full disk could take down a read-only phrasebook that has no reason to care about
     * the disk. Losing a bookmark is survivable; losing the reader is not.
     */
    private fun <T> orWrite(fallback: T, block: () -> T): T = try {
        orRecreate(block)
    } catch (e: Exception) {
        Log.w(TAG, "progress write failed; continuing without saving state.", e)
        fallback
    }

    companion object {
        private const val TAG = "LangKraftProgress"
        const val DB_NAME = "progress.db"
        const val DB_VERSION = 1
        const val TABLE_POSITION = "position"
        const val TABLE_FLAG = "failure_flag"
    }
}