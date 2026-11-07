package com.kedharsairam.langkraft

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kedharsairam.langkraft.presentation.screens.HardScreen
import com.kedharsairam.langkraft.presentation.screens.HomeScreen
import com.kedharsairam.langkraft.presentation.screens.LanguageScreen
import com.kedharsairam.langkraft.presentation.screens.NumbersScreen
import com.kedharsairam.langkraft.presentation.screens.SearchScreen
import com.kedharsairam.langkraft.presentation.theme.LangKraftTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LangKraftTheme {
                Surface(modifier = Modifier.fillMaxSize()) { LangKraftNav() }
            }
        }
    }
}

@Composable
fun LangKraftNav() {
    val nav = rememberNavController()
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // No bottom bar. Search/Hard live in top bar of Home + Language headers.
        NavHost(navController = nav, startDestination = "home", modifier = Modifier.weight(1f)) {
            composable("home") {
                HomeScreen(
                    onLang = { code -> nav.navigate("lang/$code") },
                    onSearch = { nav.navigate("search") },
                    onHard = { nav.navigate("hard") }
                )
            }
            composable("search") { SearchScreen(onOpenLang = { code -> nav.navigate("lang/$code") }) }
            composable("hard") { HardScreen(onBack = { nav.navigate("home") { popUpTo("home") } }) }
            composable("numbers") { NumbersScreen(code = "spa", onBack = { nav.navigate("home") { popUpTo("home") } }) }
            composable("lang/{code}") { entry ->
                val code = entry.arguments?.getString("code") ?: "spa"
                LanguageScreen(code = code, onBack = { nav.popBackStack() }, onNumbers = { c -> nav.navigate("numbers/$c") }, onSearch = { nav.navigate("search") }, onHard = { nav.navigate("hard") })
            }
            composable("numbers/{code}") { entry ->
                val code = entry.arguments?.getString("code") ?: "spa"
                NumbersScreen(code = code, onBack = { nav.popBackStack() })
            }
        }
    }
}
