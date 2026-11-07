package com.kedharsairam.langkraft.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("LANGKRAFT", fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onSearch, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
                IconButton(onClick = onHard, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Star, contentDescription = "Hard")
                }
            }
        }
        when (val s = state) {
            HomeUiState.Loading -> CircularProgressIndicator(Modifier.padding(top = Spacing.M))
            is HomeUiState.Failed -> Column {
                Text("Could not load languages.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { vm.load() }) { Text("Retry") }
            }
            is HomeUiState.Ready -> {
                Spacer(Modifier.height(Spacing.S))
                LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.weight(1f)) {
                    val committed = s.languages.filter { it.status == "committed" }.sortedBy { it.name }
                    val bench = s.languages.filter { it.status != "committed" }.sortedBy { it.name }
                    items(committed) { info -> LangTile(info.code, info.name, s.progress[info.code], onLang) }
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.S)) {
                            HorizontalDivider(thickness = Spacing.Hairline, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(Spacing.S))
                            Text("BENCHED", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(bench) { info -> LangTile(info.code, info.name, s.progress[info.code], onLang) }
                }
            }
        }
    }
}

@Composable
private fun LangTile(code: String, name: String, progress: Pair<Int, Int>?, onLang: (String) -> Unit) {
    val (viewed, total) = progress ?: (0 to 40)
    val frac = if (total > 0) viewed.toFloat() / total else 0f
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
        Column(modifier = Modifier.clickable(role = Role.Button) { onLang(code) }.padding(Spacing.S)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text("[$viewed/$total]", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.Xs))
            LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().height(4.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surface)
            if (viewed == 0) Text("START", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            else Text("${((frac * 100).toInt())}%", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
