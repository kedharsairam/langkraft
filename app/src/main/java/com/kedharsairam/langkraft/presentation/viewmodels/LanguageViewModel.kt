package com.kedharsairam.langkraft.presentation.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kedharsairam.langkraft.data.PhraseStore
import com.kedharsairam.langkraft.data.UserStateRepository
import com.kedharsairam.langkraft.domain.DomainResult
import com.kedharsairam.langkraft.domain.ErrorReason
import com.kedharsairam.langkraft.domain.Phrase
import com.kedharsairam.langkraft.domain.SoundCheck
import com.kedharsairam.langkraft.domain.nextIndex
import com.kedharsairam.langkraft.domain.prevIndex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface LanguageUiState {
    data object Loading : LanguageUiState
    data class Ready(
        val phrases: List<Phrase>,
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
    private val store = PhraseStore(app)
    private val repo = UserStateRepository(app)
    private val _state = MutableStateFlow<LanguageUiState>(LanguageUiState.Loading)
    val state: StateFlow<LanguageUiState> = _state.asStateFlow()
    private var lang: String = ""

    fun load(code: String) {
        lang = code
        viewModelScope.launch {
            _state.value = LanguageUiState.Loading
            repo.saveLastLang(code)
            val phrases = when (val r = store.loadPhrases(code)) {
                is DomainResult.Success -> r.data
                is DomainResult.Error -> {
                    _state.value = if (r.reason is ErrorReason.MissingAsset || r.reason is ErrorReason.Empty)
                        LanguageUiState.Empty(r.reason)
                    else LanguageUiState.Failed(r.reason)
                    return@launch
                }
            }
            val sound = (store.loadSoundCheck(code) as? DomainResult.Success)?.data
            val savedThemePos = repo.position(code, 1).first()
            val savedStar = repo.starred(code).first()
            val acked = repo.soundAcked(code).first()
            _state.value = LanguageUiState.Ready(
                phrases = phrases, theme = 1, index = savedThemePos.coerceIn(0, phrases.size - 1),
                starred = savedStar, sound = sound, soundAcked = acked
            )
        }
    }

    fun setTheme(t: Int) {
        val s = _state.value as? LanguageUiState.Ready ?: return
        viewModelScope.launch {
            val pos = repo.position(lang, t).first()
            val idx = pos.coerceIn(0, (s.phrases.filter { it.theme == t }.size - 1).coerceAtLeast(0))
            _state.value = s.copy(theme = t, index = idx, query = "")
            repo.savePosition(lang, t, idx)
        }
    }

    fun setQuery(q: String) {
        val s = _state.value as? LanguageUiState.Ready ?: return
        _state.value = s.copy(query = q)
    }

    fun next() {
        val s = _state.value as? LanguageUiState.Ready ?: return
        val count = visible(s).size
        if (count == 0) return
        val n = nextIndex(s.index, count)
        _state.value = s.copy(index = n)
        viewModelScope.launch { repo.savePosition(lang, s.theme, n) }
    }

    fun prev() {
        val s = _state.value as? LanguageUiState.Ready ?: return
        val count = visible(s).size
        if (count == 0) return
        val n = prevIndex(s.index, count)
        _state.value = s.copy(index = n)
        viewModelScope.launch { repo.savePosition(lang, s.theme, n) }
    }

    fun toggleStar(id: String) {
        val s = _state.value as? LanguageUiState.Ready ?: return
        viewModelScope.launch {
            repo.toggleStar(lang, id)
            val updated = repo.starred(lang).first()
            _state.value = s.copy(starred = updated)
        }
    }

    fun visible(s: LanguageUiState.Ready): List<Phrase> =
        s.phrases.filter { it.theme == s.theme && (s.query.isBlank() || it.english.contains(s.query, true) || it.romanization.contains(s.query, true) || it.youSay.contains(s.query, true)) }

    fun acknowledgeSound() {
        val s = _state.value as? LanguageUiState.Ready ?: return
        viewModelScope.launch {
            repo.setSoundAcked(lang)
            _state.value = s.copy(soundAcked = true)
        }
    }
}
