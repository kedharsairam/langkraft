package com.kedharsairam.langkraft.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kedharsairam.langkraft.presentation.theme.Spacing
import com.kedharsairam.langkraft.presentation.viewmodels.SearchUiState
import com.kedharsairam.langkraft.presentation.viewmodels.SearchViewModel

@Composable
fun SearchScreen(onOpenLang: (String) -> Unit, vm: SearchViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    Column(modifier = Modifier.padding(horizontal = Spacing.Screen)) {
        Spacer(Modifier.height(Spacing.S))
        Text("Search", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = "", onValueChange = { vm.search(it) },
            label = { Text("English, romanization, or You say") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.S)
        )
        when (val s = state) {
            SearchUiState.Idle -> Text("Type to search across loaded languages. Currently only Spanish has content.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            SearchUiState.Loading -> Text("Searching…")
            is SearchUiState.Ready -> {
                if (s.results.isEmpty()) Text("No matches.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else LazyColumn {
                    items(s.results) { phrase ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { onOpenLang(phrase.lang) }.padding(vertical = Spacing.S)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(phrase.english, style = MaterialTheme.typography.bodyMedium)
                                Text(phrase.youSay, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(phrase.lang.uppercase(), style = MaterialTheme.typography.labelMedium)
                        }
                        HorizontalDivider(thickness = Spacing.Hairline)
                    }
                }
            }
        }
    }
}
