package com.kedharsairam.langkraft.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kedharsairam.langkraft.presentation.theme.Spacing
import com.kedharsairam.langkraft.presentation.viewmodels.HomeUiState
import com.kedharsairam.langkraft.presentation.viewmodels.HomeViewModel

@Composable
fun HomeScreen(onLang: (String) -> Unit, vm: HomeViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    Column(modifier = Modifier.padding(Spacing.Screen)) {
        Text("LangKraft", style = MaterialTheme.typography.headlineMedium)
        Text("Open where you left it. No streaks.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = Spacing.M))
        when (val s = state) {
            HomeUiState.Loading -> Text("Loading languages…", style = MaterialTheme.typography.bodyMedium)
            is HomeUiState.Failed -> Text("Could not load languages (${s.reason::class.simpleName}). Tap to retry.", color = MaterialTheme.colorScheme.error)
            is HomeUiState.Ready -> LazyColumn {
                items(s.languages) { info ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.Xs).defaultMinSize(minHeight = Spacing.MinTarget).clickable { onLang(info.code) }) {
                        ListItem(
                            headlineContent = { Text(info.name, style = MaterialTheme.typography.titleMedium) },
                            supportingContent = { Text(if (info.status == "committed") "Committed" else "Bench — when you need it") },
                            trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = "Open ${info.name}") }
                        )
                    }
                }
            }
        }
    }
}
