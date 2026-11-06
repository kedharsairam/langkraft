package com.kedharsairam.langkraft.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.kedharsairam.langkraft.userDataStore
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserStateRepository(private val context: Context) {
    fun position(lang: String, theme: Int): Flow<Int> =
        context.userDataStore.data.map { it[Key.pos(lang, theme)]?.toIntOrNull() ?: 0 }

    suspend fun savePosition(lang: String, theme: Int, index: Int) {
        context.userDataStore.edit { it[Key.pos(lang, theme)] = index.toString() }
    }

    fun positionsFor(lang: String): Flow<Map<Int, Int>> =
        context.userDataStore.data.map { prefs ->
            (1..8).mapNotNull { t -> prefs[Key.pos(lang, t)]?.toIntOrNull()?.let { t to it } }.toMap()
        }

    fun lastLang(): Flow<String?> =
        context.userDataStore.data.map { it[Key.lastLang] }

    suspend fun saveLastLang(lang: String) {
        context.userDataStore.edit { it[Key.lastLang] = lang }
    }

    fun starred(lang: String): Flow<Set<String>> =
        context.userDataStore.data.map { it[Key.star(lang)] ?: emptySet() }

    suspend fun toggleStar(lang: String, phraseId: String) {
        context.userDataStore.edit { prefs ->
            val cur = prefs[Key.star(lang)] ?: emptySet()
            prefs[Key.star(lang)] = if (phraseId in cur) cur - phraseId else cur + phraseId
        }
    }

    fun note(lang: String, phraseId: String): Flow<String> =
        context.userDataStore.data.map { it[Key.note(lang, phraseId)] ?: "" }

    suspend fun saveNote(lang: String, phraseId: String, text: String) {
        context.userDataStore.edit { it[Key.note(lang, phraseId)] = text }
    }

    fun soundAcked(lang: String): Flow<Boolean> =
        context.userDataStore.data.map { it[Key.soundAck(lang)] == "1" }

    suspend fun setSoundAcked(lang: String) {
        context.userDataStore.edit { it[Key.soundAck(lang)] = "1" }
    }

    fun starredMap(codes: List<String>): Flow<Map<String, Set<String>>> =
        context.userDataStore.data.map { prefs ->
            codes.associateWith { prefs[Key.star(it)] ?: emptySet() }
        }
}

private object Key {
    fun pos(lang: String, theme: Int) = stringPreferencesKey("pos_${lang}_t$theme")
    fun star(lang: String) = stringSetPreferencesKey("star_$lang")
    fun note(lang: String, phraseId: String) = stringPreferencesKey("note_${lang}_$phraseId")
    val lastLang = stringPreferencesKey("last_lang")
    fun soundAck(lang: String) = stringPreferencesKey("ack_$lang")
}
