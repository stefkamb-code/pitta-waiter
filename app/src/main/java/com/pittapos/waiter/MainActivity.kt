package com.pittapos.waiter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = AppPrefs(applicationContext)
        setContent {
            PittaWaiterTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()

                    // Ένα γρήγορο διπλό/πολλαπλό tap (βιαστικός σερβιτόρος, δάχτυλο που γλιστράει) μπορεί
                    // να καλέσει navigate()/popBackStack() πολλές φορές πριν προλάβει να αλλάξει η οθόνη.
                    // Χωρίς φραγή αυτό είτε στοιβάζει το ίδιο βήμα πολλές φορές (το «πίσω» φαίνεται σαν να
                    // έχει κολλήσει, χρειάζεται πολλά πατήματα για να προχωρήσει) είτε, αν χτυπηθεί αρκετές
                    // φορές το βελάκι «πίσω» στην πρώτη πραγματική οθόνη, αδειάζει τελείως το back stack —
                    // η οθόνη μένει λευκή γιατί δεν απομένει κανένας προορισμός να δείξει το NavHost.
                    var lastNavAt by remember { mutableStateOf(0L) }
                    fun debounced(action: () -> Unit) {
                        val now = System.currentTimeMillis()
                        if (now - lastNavAt < 400) return
                        lastNavAt = now
                        action()
                    }
                    fun safeNavigate(route: String) = debounced {
                        navController.navigate(route) { launchSingleTop = true }
                    }
                    fun safeBack() = debounced {
                        if (navController.previousBackStackEntry != null) navController.popBackStack()
                    }

                    NavHost(navController = navController, startDestination = "splash") {
                        composable("splash") {
                            SplashScreen(onFinished = {
                                navController.navigate("tables") {
                                    popUpTo("splash") { inclusive = true }
                                }
                            })
                        }
                        composable("tables") {
                            TablesScreen(
                                prefs = prefs,
                                onOpenTable = { table -> safeNavigate("tableDetail/$table") },
                            )
                        }
                        composable(
                            "tableDetail/{table}",
                            arguments = listOf(navArgument("table") { type = NavType.IntType }),
                        ) { backStackEntry ->
                            val table = backStackEntry.arguments?.getInt("table") ?: 1
                            TableDetailScreen(
                                prefs = prefs,
                                table = table,
                                onAddMore = { safeNavigate("menu/$table") },
                                onBack = { safeBack() },
                            )
                        }
                        composable(
                            "menu/{table}",
                            arguments = listOf(navArgument("table") { type = NavType.IntType }),
                        ) { backStackEntry ->
                            val table = backStackEntry.arguments?.getInt("table") ?: 1
                            MenuScreen(
                                prefs = prefs,
                                table = table,
                                onDone = { safeBack() },
                            )
                        }
                    }
                }
            }
        }
    }
}
