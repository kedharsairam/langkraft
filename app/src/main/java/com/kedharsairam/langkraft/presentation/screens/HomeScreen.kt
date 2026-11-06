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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.viewmodel.compose.viewModel
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
        Text("LangKraft", style = MaterialTheme.typography.titleLarge)
        Text("Your queue. No streaks.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.M))
        when (val s = state) {
            HomeUiState.Loading -> CircularProgressIndicator()
            is HomeUiState.Failed -> Column {
                Text("Could not load languages.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { vm.load() }) { Text("Retry") }
            }
            is HomeUiState.Ready -> LazyColumn {
                // Continue hero — only if lastLang exists
                s.continue_?.let { (code, name) ->
                    item {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onLang(code) }.padding(Spacing.M),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("CONTINUE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(name, style = MaterialTheme.typography.headlineMedium)
                                    Text("Where you left off", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Filled.ChevronRight, contentDescription = null)
                            }
                        }
                        Spacer(Modifier.height(Spacing.M))
                    }
                }
                item {
                    Text("COMMITTED", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.S))
                }
                val committed = s.languages.filter { it.status == "committed" }
                items(committed) { info ->
                    val (viewed, total) = s.progress[info.code] ?: (0 to 40)
                    val frac = if (total > 0) viewed.toFloat() / total else 0f
                    Column(modifier = Modifier.clickable(role = Role.Button) { onLang(info.code) }.padding(vertical = Spacing.S)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(info.name, style = MaterialTheme.typography.headlineMedium)
                            Text("$viewed/$total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(Spacing.Xs))
                        LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().height(4.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    }
                    HorizontalDivider(thickness = Spacing.Hairline)
                }
                item {
                    Spacer(Modifier.height(Spacing.L))
                    Text("BENCH", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.S))
                }
                val bench = s.languages.filter { it.status != "committed" }
                items(bench) { info ->
                    Column(modifier = Modifier.clickable(role = Role.Button) { onLang(info.code) }.padding(vertical = Spacing.S)) {
                        Text(info.name, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("When you need it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(thickness = Spacing.Hairline)
                }
            }
        }
    }
}
