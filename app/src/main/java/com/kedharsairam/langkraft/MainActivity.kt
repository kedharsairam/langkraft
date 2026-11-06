package com.kedharsairam.langkraft

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val tabs = listOf(
        "home" to "QUEUE",
        "search" to "SEARCH",
        "numbers" to "NUMBERS",
        "hard" to "HARD"
    )
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    // Operator Deck: static bottom text dock — always visible, no icons.
    Column(modifier = Modifier.fillMaxSize()) {
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
                LanguageScreen(code = code, onBack = { nav.popBackStack() }, onNumbers = { c -> nav.navigate("numbers/$c") })
            }
            composable("numbers/{code}") { entry ->
                val code = entry.arguments?.getString("code") ?: "spa"
                NumbersScreen(code = code, onBack = { nav.popBackStack() })
            }
        }
        Row(modifier = Modifier.fillMaxWidth().height(56.dp)) {
            tabs.forEach { (route, label) ->
                val selected = currentRoute?.startsWith(route) == true || (route == "home" && currentRoute?.startsWith("lang/") == true)
                TextButton(
                    onClick = { nav.navigate(route) { popUpTo("home") { inclusive = false }; launchSingleTop = true } },
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 56.dp)
                ) {
                    Text(
                        label,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
