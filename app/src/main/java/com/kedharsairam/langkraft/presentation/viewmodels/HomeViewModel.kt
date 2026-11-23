package com.kedharsairam.langkraft.presentation.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kedharsairam.langkraft.data.ContentRepository
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Ready(
        val languages: List<LanguageInfo>,
        val progress: Map<String, Pair<Int, Int>> = emptyMap(),
        val phraseCounts: Map<String, Int> = emptyMap(),
        val lessonCounts: Map<String, Int> = emptyMap(),
        val continue_: Pair<String, String>? = null,
        val starredCount: Int = 0
    ) : HomeUiState
    data class Failed(val reason: ErrorReason) : HomeUiState
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val store: ContentRepository = (app as com.kedharsairam.langkraft.LangKraftApp).content
    private val repo = UserStateRepository(app)
    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = HomeUiState.Loading
            val langs = when (val r = store.catalog()) {
                is DomainResult.Success -> r.data
                is DomainResult.Error -> { _state.value = HomeUiState.Failed(r.reason); return@launch }
            }
            val last = repo.lastLang().first()
            val codes = langs.map { it.code }
            val starred = repo.starredMap(codes).first()
            val starredCount = starred.values.sumOf { it.size }
            val progress = mutableMapOf<String, Pair<Int, Int>>()
            val phraseCounts = mutableMapOf<String, Int>()
            val lessonCounts = mutableMapOf<String, Int>()
            for (info in langs) {
                val phrases = (store.phrases(info.code) as? DomainResult.Success)?.data ?: emptyList()
                val visited = repo.visited(info.code).first()
                val lessons = phrases.map { it.theme }.distinct().size.coerceAtLeast(1)
                phraseCounts[info.code] = phrases.size
                lessonCounts[info.code] = lessons
                // Progress is lessons opened out of lessons available — not phrase positions.
                progress[info.code] = visited.size.coerceAtMost(lessons) to lessons
            }
            val continueInfo = last?.let { code ->
                val name = langs.find { it.code == code }?.name ?: code.uppercase()
                code to name
            }
            _state.value = HomeUiState.Ready(langs, progress, phraseCounts, lessonCounts, continueInfo, starredCount)
        }
    }
}

sealed interface HardUiState {
    data object Loading : HardUiState
    data class Ready(val phrases: List<HardEntry>) : HardUiState
    data class Failed(val reason: ErrorReason) : HardUiState
}

/** A hard phrase plus where it lives, so a tap can open the exact lesson. */
data class HardEntry(val phrase: Phrase, val lang: String)

class HardViewModel(app: Application) : AndroidViewModel(app) {
    private val store: ContentRepository = (app as com.kedharsairam.langkraft.LangKraftApp).content
    private val repo = UserStateRepository(app)
    private val _state = MutableStateFlow<HardUiState>(HardUiState.Loading)
    val state: StateFlow<HardUiState> = _state.asStateFlow()

    init { load() }
    fun load() {
        viewModelScope.launch {
            val catalog = (store.catalog() as? DomainResult.Success)?.data ?: emptyList()
            val starred = repo.starredMap(catalog.map { it.code }).first()
            val all = mutableListOf<HardEntry>()
            for ((code, ids) in starred) {
                if (ids.isEmpty()) continue
                val phrases = (store.phrases(code) as? DomainResult.Success)?.data ?: emptyList()
                all += phrases.filter { it.id in ids }.map { HardEntry(it, code) }
            }
            _state.value = HardUiState.Ready(all)
        }
    }
}

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Ready(val results: List<Phrase>, val truncated: Boolean = false) : SearchUiState
    data class Failed(val reason: String) : SearchUiState
}

class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val store: ContentRepository = (app as com.kedharsairam.langkraft.LangKraftApp).content
    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var searchJob: kotlinx.coroutines.Job? = null

    fun search(q: String) {
        searchJob?.cancel()                       // newest query wins
        searchJob = viewModelScope.launch {
            if (q.isBlank()) { _state.value = SearchUiState.Idle; return@launch }
            delay(200)                            // debounce typing
            if (!isActive) return@launch
            _state.value = SearchUiState.Loading
            val catalog = when (val r = store.catalog()) {
                is DomainResult.Success -> r.data
                is DomainResult.Error -> { _state.value = SearchUiState.Failed("Could not load catalogue"); return@launch }
            }
            val hits = mutableListOf<Phrase>()
            var more = false
            for (info in catalog) {
                val phrases = when (val r = store.phrases(info.code)) {
                    is DomainResult.Success -> r.data
                    is DomainResult.Error -> { _state.value = SearchUiState.Failed("Could not load ${info.name}"); return@launch }
                }
                for (p in phrases) {
                    if (p.english.contains(q, true) || p.romanization.contains(q, true) || p.youSay.contains(q, true)) {
                        if (hits.size < 60) hits += p else { more = true; break }
                    }
                }
            }
            _state.value = SearchUiState.Ready(hits, more)
        }
    }
    fun clear() { searchJob?.cancel(); _state.value = SearchUiState.Idle }
}
