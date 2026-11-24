package com.kedharsairam.langkraft.presentation.screens

import com.kedharsairam.langkraft.presentation.components.displaySource

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kedharsairam.langkraft.presentation.components.*
import com.kedharsairam.langkraft.presentation.components.ReloadOnResume
import com.kedharsairam.langkraft.presentation.theme.*
import com.kedharsairam.langkraft.presentation.viewmodels.HardUiState
import com.kedharsairam.langkraft.presentation.viewmodels.HardViewModel
import compose.icons.TablerIcons
import compose.icons.tablericons.Star

@Composable
fun HardScreen(
    onBack: () -> Unit,
    onOpenPhrase: (String, Int, String) -> Unit = { _, _, _ -> },
    vm: HardViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    ReloadOnResume { vm.load() }
    Box(Modifier.fillMaxSize().worldMapDots()) {
        Column(Modifier.padding(horizontal = Spacing.Screen).fillMaxSize()) {
            KTopBar("Hard", onBack = onBack)
            Spacer(Modifier.height(Spacing.S))
            Text("Your starred pile. Review when you want.", style = LangTypography.bodySmall, color = LangColors.Muted)
            Spacer(Modifier.height(Spacing.M))
            when (val s = state) {
                HardUiState.Loading -> KLabel("LOADING…")
                is HardUiState.Failed -> Text("Could not load hard pile.", color = LangColors.Muted)
                is HardUiState.Ready -> {
                    if (s.phrases.isEmpty()) {
                        Row(Modifier.fillMaxWidth().kraftPanel().padding(Spacing.M)) {
                            Text("No hard phrases yet. Star any phrase to add it here.", color = LangColors.Ink)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            items(s.phrases, key = { it.phrase.id }) { entry ->
                                Column(Modifier.fillMaxWidth().kraftPanel()
                                    .kraftClick { onOpenPhrase(entry.lang, entry.phrase.theme, entry.phrase.id) }.padding(Spacing.M)) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(entry.phrase.english, style = LangTypography.bodyMedium,
                                            color = LangColors.Ink, modifier = Modifier.weight(1f))
                                        Icon(TablerIcons.Star, contentDescription = "Hard", tint = LangColors.StateHard, modifier = Modifier.size(22.dp))
                                    }
                                    Spacer(Modifier.height(Spacing.Xs))
                                    Text(entry.phrase.romanization, style = LangTypography.headlineMedium, color = LangColors.Ink)
                                    Spacer(Modifier.height(Spacing.Xs))
                                    Text(entry.phrase.youSay.replace("-", "-\u200B"), style = YouSayStyle,
                                        fontSize = 18.sp, color = LangColors.Accent)
                                    Spacer(Modifier.height(Spacing.Xs))
                                    KLabel("${entry.lang.uppercase()} · ${displaySource(entry.phrase.source)} · ${entry.phrase.checked}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
