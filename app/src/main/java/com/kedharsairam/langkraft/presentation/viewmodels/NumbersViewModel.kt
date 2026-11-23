package com.kedharsairam.langkraft.presentation.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kedharsairam.langkraft.data.ContentRepository
import com.kedharsairam.langkraft.domain.DomainResult
import com.kedharsairam.langkraft.domain.NumberEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface NumbersUiState {
    data object Loading : NumbersUiState
    data class Ready(val entries: List<NumberEntry>) : NumbersUiState
    data class Failed(val message: String) : NumbersUiState
}

class NumbersViewModel(app: Application) : AndroidViewModel(app) {
    private val store: ContentRepository = (app as com.kedharsairam.langkraft.LangKraftApp).content
    private val _state = MutableStateFlow<NumbersUiState>(NumbersUiState.Loading)
    val state: StateFlow<NumbersUiState> = _state.asStateFlow()

    fun load(code: String) {
        viewModelScope.launch {
            _state.value = NumbersUiState.Loading
            when (val r = store.numbers(code)) {
                is DomainResult.Success -> _state.value = NumbersUiState.Ready(r.data)
                is DomainResult.Error -> _state.value = NumbersUiState.Failed(r.reason::class.simpleName ?: "error")
            }
        }
    }
}
