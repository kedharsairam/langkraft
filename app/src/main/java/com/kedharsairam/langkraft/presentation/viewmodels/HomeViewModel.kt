package com.kedharsairam.langkraft.presentation.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kedharsairam.langkraft.data.PhraseStore
import com.kedharsairam.langkraft.domain.DomainResult
import com.kedharsairam.langkraft.domain.ErrorReason
import com.kedharsairam.langkraft.domain.LanguageInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Ready(val languages: List<LanguageInfo>) : HomeUiState
    data class Failed(val reason: ErrorReason) : HomeUiState
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val store = PhraseStore(app)
    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = HomeUiState.Loading
            when (val result = store.loadCatalog()) {
                is DomainResult.Success -> _state.value = HomeUiState.Ready(result.data)
                is DomainResult.Error -> _state.value = HomeUiState.Failed(result.reason)
            }
        }
    }
}
