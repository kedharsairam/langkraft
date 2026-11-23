package com.kedharsairam.langkraft.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kedharsairam.langkraft.presentation.components.KraftTopBar
import com.kedharsairam.langkraft.presentation.components.MonoLabel
import com.kedharsairam.langkraft.presentation.components.borderedCard
import com.kedharsairam.langkraft.presentation.components.flatClickable
import com.kedharsairam.langkraft.presentation.theme.LangColors
import com.kedharsairam.langkraft.presentation.theme.Spacing
import com.kedharsairam.langkraft.presentation.viewmodels.SearchUiState
import com.kedharsairam.langkraft.presentation.viewmodels.SearchViewModel

@Composable
fun SearchScreen(onOpenLang: (String) -> Unit, onBack: (() -> Unit)? = null, vm: SearchViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var query by remember { mutableStateOf("") }
    Column(Modifier.padding(horizontal = Spacing.Screen).fillMaxSize()) {
        KraftTopBar("Search", onBack = onBack)
        Spacer(Modifier.height(Spacing.S))
        Box(Modifier.fillMaxWidth().borderedCard().padding(Spacing.M)) {
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = { query = it; vm.search(it) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace, fontSize = 16.sp, color = LangColors.Ink
                ),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search phrases by English, romanization or you say" },
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("English, romanization, or you say", color = LangColors.Muted,
                        fontFamily = FontFamily.Monospace, fontSize = 16.sp)
                    inner()
                }
            )
        }
        Spacer(Modifier.height(Spacing.S))
        when (val s = state) {
            SearchUiState.Idle -> Text("Search across 19 languages by English, romanization or you say.", color = LangColors.Muted)
            SearchUiState.Loading -> MonoLabel("SEARCHING…")
            is SearchUiState.Ready -> {
                if (s.results.isEmpty()) {
                    Text("No matches.", color = LangColors.Muted)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        // Group by language so results read as a comparison, not a flat list.
                        val grouped = s.results.groupBy { it.lang }
                        grouped.forEach { (lang, phrases) ->
                            item(key = "head-$lang") { MonoLabel(lang.uppercase()) }
                            items(phrases, key = { it.id }) { phrase ->
                                Row(
                                    Modifier.fillMaxWidth().borderedCard().flatClickable { onOpenLang(lang) }.padding(Spacing.M),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(phrase.english, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = LangColors.Ink)
                                        Spacer(Modifier.height(Spacing.Xs))
                                        Text(phrase.romanization, fontFamily = FontFamily.Monospace, fontSize = 16.sp, color = LangColors.Muted)
                                        Spacer(Modifier.height(Spacing.Xs))
                                        Text(phrase.youSay, fontFamily = FontFamily.Monospace,
                                            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LangColors.Ink)
                                    }
                                }
                            }
                        }
                        if (s.truncated) {
                            item(key = "truncated") {
                                Spacer(Modifier.height(Spacing.S))
                                MonoLabel("SHOWING FIRST 60 — NARROW YOUR SEARCH")
                            }
                        }
                    }
                }
            }
        }
    }
}
