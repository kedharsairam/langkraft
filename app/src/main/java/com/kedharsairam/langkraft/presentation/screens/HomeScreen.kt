package com.kedharsairam.langkraft.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kedharsairam.langkraft.domain.LanguageInfo
import com.kedharsairam.langkraft.presentation.theme.Spacing
import com.kedharsairam.langkraft.presentation.viewmodels.HomeUiState
import com.kedharsairam.langkraft.presentation.viewmodels.HomeViewModel

@Composable
fun HomeScreen(onLang: (String) -> Unit, vm: HomeViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    Column(modifier = Modifier.padding(horizontal = Spacing.Screen)) {
        Spacer(Modifier.height(Spacing.M))
        Text("LangKraft", style = MaterialTheme.typography.titleLarge)
        Text("Open where you left it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.L))
        when (val s = state) {
            HomeUiState.Loading -> Text("Loading…", style = MaterialTheme.typography.bodyMedium)
            is HomeUiState.Failed -> Text("Could not load languages. Tap to retry.", color = MaterialTheme.colorScheme.error)
            is HomeUiState.Ready -> {
                val committed = s.languages.filter { it.status == "committed" }
                val bench = s.languages.filter { it.status != "committed" }
                LazyColumn {
                    item {
                        Text("COMMITTED", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(Spacing.S))
                    }
                    items(committed) { info -> LanguageRow(info, onLang) }
                    item {
                        Spacer(Modifier.height(Spacing.L))
                        Text("BENCH", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(Spacing.S))
                    }
                    items(bench) { info -> LanguageRow(info, onLang, muted = true) }
                }
            }
        }
    }
}

@Composable
fun LanguageRow(info: LanguageInfo, onLang: (String) -> Unit, muted: Boolean = false) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Spacing.MinTarget)
                .clickable(role = Role.Button) { onLang(info.code) }
                .padding(vertical = Spacing.S)
                .semantics { contentDescription = "Open ${info.name}" },
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(info.name, style = MaterialTheme.typography.headlineMedium, color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                if (!muted) Text("0/${info.kitTotal}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Text("When you need it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(thickness = Spacing.Hairline, color = MaterialTheme.colorScheme.outline)
    }
}
