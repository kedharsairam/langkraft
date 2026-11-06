package com.kedharsairam.langkraft

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
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
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination
    // Your picks: bottom icon bar (Q6), icons only no labels (Q4), filled accent (Q5), numbers inside language (Q9)
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        NavHost(navController = nav, startDestination = "home", modifier = Modifier.weight(1f)) {
            composable("home") {
                HomeScreen(onLang = { code -> nav.navigate("lang/$code") }, onSearch = { nav.navigate("search") }, onHard = { nav.navigate("hard") })
            }
            composable("search") { SearchScreen(onOpenLang = { code -> nav.navigate("lang/$code") }) }
            composable("hard") { HardScreen(onBack = { nav.navigate("home") { popUpTo("home") } }) }
            composable("lang/{code}") { e ->
                val code = e.arguments?.getString("code") ?: "spa"
                LanguageScreen(code = code, onBack = { nav.popBackStack() }, onNumbers = { c -> nav.navigate("numbers/$c") })
            }
            composable("numbers/{code}") { e ->
                val code = e.arguments?.getString("code") ?: "spa"
                NumbersScreen(code = code, onBack = { nav.popBackStack() })
            }
        }
        // Hide bottom bar only in language/numbers detail for focus
        val inLang = current?.hierarchy?.any { it.route?.startsWith("lang/") == true || it.route?.startsWith("numbers/") == true } == true
        if (!inLang) {
            NavigationBar(modifier = Modifier.navigationBarsPadding()) {
                val isHome = current?.hierarchy?.any { it.route == "home" } == true
                val isSearch = current?.hierarchy?.any { it.route == "search" } == true
                val isHard = current?.hierarchy?.any { it.route == "hard" } == true
                NavigationBarItem(selected = isHome, onClick = { nav.navigate("home") { popUpTo("home"); launchSingleTop = true } }, icon = { Icon(Icons.Filled.Home, contentDescription = "Queue") }, label = null)
                NavigationBarItem(selected = isSearch, onClick = { nav.navigate("search") { popUpTo("home"); launchSingleTop = true } }, icon = { Icon(Icons.Filled.Search, contentDescription = "Search") }, label = null)
                NavigationBarItem(selected = isHard, onClick = { nav.navigate("hard") { popUpTo("home"); launchSingleTop = true } }, icon = { Icon(Icons.Filled.Star, contentDescription = "Hard") }, label = null)
            }
        }
    }
}
