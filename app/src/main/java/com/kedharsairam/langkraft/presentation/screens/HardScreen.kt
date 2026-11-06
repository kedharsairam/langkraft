package com.kedharsairam.langkraft.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kedharsairam.langkraft.presentation.theme.Spacing
import com.kedharsairam.langkraft.presentation.viewmodels.HardUiState
import com.kedharsairam.langkraft.presentation.viewmodels.HardViewModel

@Composable
fun HardScreen(onBack: () -> Unit, vm: HardViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    Column(modifier = Modifier.padding(horizontal = Spacing.Screen)) {
        Spacer(Modifier.height(Spacing.S))
        TextButton(onClick = onBack, modifier = Modifier.defaultMinSize(minHeight = Spacing.MinTarget)) { Text("← Home") }
        Text("Hard", style = MaterialTheme.typography.titleLarge)
        Text("Your starred pile. Review when you want.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.M))
        when (val s = state) {
            HardUiState.Loading -> Text("Loading…")
            is HardUiState.Failed -> Text("Could not load hard pile.")
            is HardUiState.Ready -> {
                if (s.phrases.isEmpty()) Text("No starred phrases yet. Tap the star on a phrase you find hard.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else LazyColumn {
                    items(s.phrases) { phrase ->
                        Column(modifier = Modifier.padding(vertical = Spacing.S)) {
                            Text(phrase.english, style = MaterialTheme.typography.bodyMedium)
                            Text(phrase.youSay, style = MaterialTheme.typography.bodySmall)
                            Text("${phrase.lang.uppercase()} · ${phrase.id}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider(thickness = Spacing.Hairline)
                    }
                }
            }
        }
    }
}
