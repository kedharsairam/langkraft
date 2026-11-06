package com.kedharsairam.langkraft

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kedharsairam.langkraft.data.PhraseStore
import com.kedharsairam.langkraft.data.UserStateRepository
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LangKraftNav()
                }
            }
        }
    }
}

@Composable
fun LangKraftNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") { HomeScreen(onLang = { code -> nav.navigate("lang/$code") }) }
        composable("lang/{code}") { backStackEntry ->
            val code = backStackEntry.arguments?.getString("code") ?: "spa"
            LanguageScreen(code = code, onBack = { nav.popBackStack() })
        }
    }
}

@Composable
fun HomeScreen(onLang: (String) -> Unit) {
    val ctx = LocalContext.current
    val store = remember { PhraseStore(ctx) }
    var langs by remember { mutableStateOf(emptyList<com.kedharsairam.langkraft.domain.LanguageInfo>()) }
    LaunchedEffect(Unit) { langs = runCatching { store.loadCatalog() }.getOrDefault(emptyList()) }
    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item { Text("LangKraft", style = MaterialTheme.typography.headlineMedium); Text("Open where you left it. No streaks.", modifier = Modifier.padding(bottom = 12.dp)) }
        items(langs) { info ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onLang(info.code) }) {
                ListItem(headlineContent = { Text(info.name) }, supportingContent = { Text(if (info.status == "committed") "Committed" else "Bench — when you need it") }, trailingContent = { Text("›") })
            }
        }
    }
}

@Composable
fun LanguageScreen(code: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { PhraseStore(ctx) }
    val repo = remember { UserStateRepository(ctx) }
    var phrases by remember { mutableStateOf(emptyList<com.kedharsairam.langkraft.domain.Phrase>()) }
    var sound by remember { mutableStateOf<com.kedharsairam.langkraft.domain.SoundCheck?>(null) }
    var theme by remember { mutableIntStateOf(1) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(code) {
        phrases = runCatching { store.loadPhrases(code) }.getOrDefault(emptyList())
        sound = runCatching { store.loadSoundCheck(code) }.getOrNull()
    }
    val visible = phrases.filter { it.theme == theme && (query.isBlank() || it.english.contains(query, true) || it.romanization.contains(query, true)) }
    Column(modifier = Modifier.padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← Languages") }
        Text(code.uppercase(), style = MaterialTheme.typography.headlineSmall)
        sound?.let { sc ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Sound check", style = MaterialTheme.typography.titleMedium)
                    Text("New sounds: ${sc.newSounds}")
                    Text("Rhythm: ${sc.rhythm}")
                    Text("Learn first: ${sc.registerFirst}")
                    Text("Trap: ${sc.trap}")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..8).forEach { t ->
                FilterChip(selected = theme == t, onClick = { theme = t }, label = { Text(t.toString()) })
            }
        }
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search English") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        PhraseList(phrases = visible, lang = code, repo = repo)
    }
}

@Composable
fun PhraseList(phrases: List<com.kedharsairam.langkraft.domain.Phrase>, lang: String, repo: UserStateRepository) {
    if (phrases.isEmpty()) { Text("No phrases yet for this theme."); return }
    var starred by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(lang) { repo.starred(lang).collect { starred = it } }
    var index by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val phrase = phrases[index.coerceIn(0, phrases.lastIndex)]
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(phrase.english, fontSize = 24.sp)
            var hidden by remember(phrase.id) { mutableStateOf(false) }
            if (!hidden) {
                Text("You say: ", style = MaterialTheme.typography.labelLarge)
                Text(phrase.youSay, fontSize = 32.sp)
                Text(phrase.soundTip, style = MaterialTheme.typography.bodySmall)
                Text(phrase.note, style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Hidden — say it aloud.", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { hidden = !hidden }) { Text(if (hidden) "Reveal" else "Hide") }
                OutlinedButton(onClick = { scope.launch { repo.toggleStar(lang, phrase.id) } }) { Text(if (phrase.id in starred) "★ Hard" else "☆ Mark hard") }
            }
            var noteText by remember(phrase.id) { mutableStateOf("") }
            LaunchedEffect(phrase.id) { repo.note(lang, phrase.id).collect { noteText = it } }
            OutlinedTextField(value = noteText, onValueChange = { noteText = it }, label = { Text("Private note") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            TextButton(onClick = { scope.launch { repo.saveNote(lang, phrase.id, noteText) } }) { Text("Save note") }
            Spacer(Modifier.height(8.dp))
            Text("Source: ${phrase.source} • Checked ${phrase.checked}")
        }
    }
    Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { index = (index - 1 + phrases.size) % phrases.size }) { Text("← Prev") }
        Text("${index + 1} / ${phrases.size}")
        TextButton(onClick = { index = (index + 1) % phrases.size }) { Text("Next →") }
    }
}
