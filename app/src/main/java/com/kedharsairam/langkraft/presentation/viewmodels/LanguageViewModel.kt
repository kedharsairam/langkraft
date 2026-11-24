package com.kedharsairam.langkraft.presentation.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kedharsairam.langkraft.data.ContentRepository
import com.kedharsairam.langkraft.data.UserStateRepository
import com.kedharsairam.langkraft.domain.DomainResult
import com.kedharsairam.langkraft.domain.ErrorReason
import com.kedharsairam.langkraft.domain.Phrase
import com.kedharsairam.langkraft.domain.SoundCheck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface LanguageUiState {
    data object Loading : LanguageUiState
    data class Ready(
        val phrases: List<Phrase>,
        val name: String = "",
        val theme: Int = 1,
        val index: Int = 0,
        val query: String = "",
        val starred: Set<String> = emptySet(),
        val sound: SoundCheck? = null,
        val soundAcked: Boolean = false
    ) : LanguageUiState
    data class Empty(val reason: ErrorReason) : LanguageUiState
    data class Failed(val reason: ErrorReason) : LanguageUiState
}

class LanguageViewModel(app: Application) : AndroidViewModel(app) {
    private val store: ContentRepository = (app as com.kedharsairam.langkraft.LangKraftApp).content
    private val repo = UserStateRepository(app)
    private val _state = MutableStateFlow<LanguageUiState>(LanguageUiState.Loading)
    val state: StateFlow<LanguageUiState> = _state.asStateFlow()
    private var lang: String = ""

    fun load(code: String) {
        lang = code
        viewModelScope.launch {
            _state.value = LanguageUiState.Loading
            val phrases = when (val r = store.phrases(code)) {
                is DomainResult.Success -> r.data
                is DomainResult.Error -> {
                    _state.value = if (r.reason is ErrorReason.MissingAsset || r.reason is ErrorReason.Empty)
                        LanguageUiState.Empty(r.reason)
                    else LanguageUiState.Failed(r.reason)
                    return@launch
                }
            }
            val sound = (store.soundCheck(code) as? DomainResult.Success)?.data
            val name = (store.catalog() as? DomainResult.Success)?.data?.firstOrNull { it.code == code }?.name ?: code.uppercase()
            repo.saveLastLang(code)
            val savedThemePos = repo.position(code, 1).first()
            val savedStar = repo.starred(code).first()
            val acked = repo.soundAcked(code).first()
            // Landing on a language shows lesson 1 — count it as visited too,
            // so the deep-link and normal paths produce the same progress.
            repo.markVisited(code, 1)
            _state.value = LanguageUiState.Ready(
                phrases = phrases, name = name, theme = 1, index = savedThemePos.coerceIn(0, phrases.size - 1),
                starred = savedStar, sound = sound, soundAcked = acked
            )
        }
    }

    fun setTheme(t: Int) {
        val s = _state.value as? LanguageUiState.Ready ?: return
        _state.value = s.copy(theme = t, index = 0, query = "")
        viewModelScope.launch { repo.markVisited(lang, t) }
    }

    fun setQuery(q: String) {
        val s = _state.value as? LanguageUiState.Ready ?: return
        _state.value = s.copy(query = q)
    }

    /** Step within a lesson, stopping at the ends — a phrasebook stepper should not wrap. */
    fun next() {
        val s = _state.value as? LanguageUiState.Ready ?: return
        val count = visible(s).size
        if (count == 0) return
        val n = (s.index + 1).coerceIn(0, count - 1)
        if (n == s.index) return
        _state.value = s.copy(index = n)
        viewModelScope.launch { repo.savePosition(lang, s.theme, n) }
    }

    fun prev() {
        val s = _state.value as? LanguageUiState.Ready ?: return
        val count = visible(s).size
        if (count == 0) return
        val n = (s.index - 1).coerceIn(0, count - 1)
        if (n == s.index) return
        _state.value = s.copy(index = n)
        viewModelScope.launch { repo.savePosition(lang, s.theme, n) }
    }

    /** Open a lesson at an exact phrase, used by search and Hard deep links. */
    fun jumpTo(theme: Int, phraseId: String?) {
        val s = _state.value as? LanguageUiState.Ready ?: return
        val list = s.phrases.filter { it.theme == theme }
        if (list.isEmpty()) return
        val raw = phraseId?.let { id -> list.indexOfFirst { it.id == id } } ?: 0
        val n = raw.coerceAtLeast(0).coerceIn(0, list.size - 1)
        _state.value = s.copy(theme = theme, index = n, query = "")
        viewModelScope.launch {
            repo.markVisited(lang, theme)
            repo.savePosition(lang, theme, n)
        }
    }

    fun toggleStar(id: String) {
        viewModelScope.launch {
            repo.toggleStar(lang, id)
            val updated = repo.starred(lang).first()
            // Re-read current state after the suspend — never clobber a concurrent change.
            val current = _state.value as? LanguageUiState.Ready ?: return@launch
            _state.value = current.copy(starred = updated)
        }
    }

    fun visible(s: LanguageUiState.Ready): List<Phrase> =
        s.phrases.filter { it.theme == s.theme && (s.query.isBlank() || it.english.contains(s.query, true) || it.romanization.contains(s.query, true) || it.youSay.contains(s.query, true)) }

    fun acknowledgeSound() {
        viewModelScope.launch {
            repo.setSoundAcked(lang)
            val current = _state.value as? LanguageUiState.Ready ?: return@launch
            _state.value = current.copy(soundAcked = true)
        }
    }
}
