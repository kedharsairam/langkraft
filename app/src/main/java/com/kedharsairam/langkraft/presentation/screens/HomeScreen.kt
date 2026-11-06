package com.kedharsairam.langkraft.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
fun HomeScreen(
    onLang: (String) -> Unit,
    onSearch: () -> Unit,
    onHard: () -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    Column(modifier = Modifier.padding(horizontal = Spacing.Screen)) {
        Spacer(Modifier.height(Spacing.M))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("LangKraft", style = MaterialTheme.typography.titleLarge)
                Text("Open where you left it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row {
                TextButton(onClick = onSearch, modifier = Modifier.defaultMinSize(minHeight = Spacing.MinTarget)) { Text("Search") }
                TextButton(onClick = onHard, modifier = Modifier.defaultMinSize(minHeight = Spacing.MinTarget)) { Text("Hard") }
            }
        }
        Spacer(Modifier.height(Spacing.M))
        when (val s = state) {
            HomeUiState.Loading -> CircularProgressIndicator()
            is HomeUiState.Failed -> Column {
                Text("Could not load languages.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { vm.load() }) { Text("Retry") }
            }
            is HomeUiState.Ready -> LazyColumn {
                s.continue_?.let { (code, name) ->
                    item {
                        Column(modifier = Modifier.clickable(role = Role.Button) { onLang(code) }.padding(vertical = Spacing.S)) {
                            Text("CONTINUE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(name, style = MaterialTheme.typography.headlineMedium)
                        }
                        HorizontalDivider(thickness = Spacing.Hairline)
                    }
                }
                item {
                    Text("COMMITTED", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.S))
                }
                val committed = s.languages.filter { it.status == "committed" }
                items(committed) { info -> LanguageRow(info, s.progress[info.code] ?: (0 to 40), onLang) }
                item {
                    Spacer(Modifier.height(Spacing.L))
                    Text("BENCH", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.S))
                }
                val bench = s.languages.filter { it.status != "committed" }
                items(bench) { info -> LanguageRow(info, s.progress[info.code] ?: (0 to 40), onLang, muted = true) }
            }
        }
    }
}

@Composable
fun LanguageRow(info: LanguageInfo, progress: Pair<Int, Int>, onLang: (String) -> Unit, muted: Boolean = false) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = Spacing.MinTarget)
                .clickable(role = Role.Button) { onLang(info.code) }.padding(vertical = Spacing.S),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(info.name, style = MaterialTheme.typography.headlineMedium, color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                if (!muted) Text("${progress.first}/${progress.second}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Text("When you need it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(thickness = Spacing.Hairline, color = MaterialTheme.colorScheme.outline)
    }
}
