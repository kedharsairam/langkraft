package com.kedharsairam.langkraft.presentation.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kedharsairam.langkraft.data.PhraseStore
import com.kedharsairam.langkraft.data.UserStateRepository
import com.kedharsairam.langkraft.domain.DomainResult
import com.kedharsairam.langkraft.domain.ErrorReason
import com.kedharsairam.langkraft.domain.LanguageInfo
import com.kedharsairam.langkraft.domain.Phrase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Ready(
        val languages: List<LanguageInfo>,
        val progress: Map<String, Pair<Int, Int>> = emptyMap(),
        val continue_: Pair<String, String>? = null,
        val starredCount: Int = 0
    ) : HomeUiState
    data class Failed(val reason: ErrorReason) : HomeUiState
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val store = PhraseStore(app)
    private val repo = UserStateRepository(app)
    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = HomeUiState.Loading
            val langs = when (val r = store.loadCatalog()) {
                is DomainResult.Success -> r.data
                is DomainResult.Error -> { _state.value = HomeUiState.Failed(r.reason); return@launch }
            }
            val last = repo.lastLang().first()
            val codes = langs.map { it.code }
            val starred = repo.starredMap(codes).first()
            val starredCount = starred.values.sumOf { it.size }
            val progress = mutableMapOf<String, Pair<Int, Int>>()
            for (info in langs) {
                val phrases = (store.loadPhrases(info.code) as? DomainResult.Success)?.data ?: emptyList()
                val pos = repo.positionsFor(info.code).first()
                val viewed = pos.values.sumOf { it + 1 }
                progress[info.code] = viewed to phrases.size.coerceAtLeast(info.kitTotal)
            }
            val continueInfo = last?.let { code ->
                val name = langs.find { it.code == code }?.name ?: code.uppercase()
                code to name
            }
            _state.value = HomeUiState.Ready(langs, progress, continueInfo, starredCount)
        }
    }
}

sealed interface HardUiState {
    data object Loading : HardUiState
    data class Ready(val phrases: List<Phrase>) : HardUiState
    data class Failed(val reason: ErrorReason) : HardUiState
}

class HardViewModel(app: Application) : AndroidViewModel(app) {
    private val store = PhraseStore(app)
    private val repo = UserStateRepository(app)
    private val _state = MutableStateFlow<HardUiState>(HardUiState.Loading)
    val state: StateFlow<HardUiState> = _state.asStateFlow()

    init { load() }
    fun load() {
        viewModelScope.launch {
            val catalog = (store.loadCatalog() as? DomainResult.Success)?.data ?: emptyList()
            val starred = repo.starredMap(catalog.map { it.code }).first()
            val all = mutableListOf<Phrase>()
            for ((code, ids) in starred) {
                if (ids.isEmpty()) continue
                val phrases = (store.loadPhrases(code) as? DomainResult.Success)?.data ?: emptyList()
                all += phrases.filter { it.id in ids }
            }
            _state.value = HardUiState.Ready(all)
        }
    }
}

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Ready(val results: List<Phrase>) : SearchUiState
}

class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val store = PhraseStore(app)
    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    fun search(q: String) {
        viewModelScope.launch {
            if (q.isBlank()) { _state.value = SearchUiState.Idle; return@launch }
            _state.value = SearchUiState.Loading
            val catalog = (store.loadCatalog() as? DomainResult.Success)?.data ?: emptyList()
            val hits = mutableListOf<Phrase>()
            for (info in catalog) {
                val phrases = (store.loadPhrases(info.code) as? DomainResult.Success)?.data ?: continue
                hits += phrases.filter { it.english.contains(q, true) || it.romanization.contains(q, true) || it.youSay.contains(q, true) }
                if (hits.size > 60) break
            }
            _state.value = SearchUiState.Ready(hits)
        }
    }
    fun clear() { _state.value = SearchUiState.Idle }
}
