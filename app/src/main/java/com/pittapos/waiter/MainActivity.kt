package com.pittapos.waiter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.navigation.compose.currentBackStackEntryAsState
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

                    // Το ίδιο πρόβλημα (και η ίδια λευκή οθόνη) μπορεί να ξαναγίνει και από το φυσικό/gesture
                    // κουμπί «πίσω» της συσκευής, όχι μόνο από το δικό μας βελάκι: αυτό περνάει κατευθείαν από
                    // τον εσωτερικό χειριστή του NavHost, χωρίς κανένα ντεμπάουνς. Ένας βιαστικός σερβιτόρος
                    // που πατάει επανειλημμένα το πίσω της συσκευής μπορεί να αδειάσει το back stack πιο γρήγορα
                    // απ' όσο προλαβαίνει να ανανεωθεί η οθόνη. Το πιάνουμε εδώ, μία φορά, κεντρικά, και το
                    // περνάμε από το ίδιο debounce με το βελάκι· ο πιο εσωτερικός BackHandler του MenuScreen
                    // (κατηγορία -> λίστα κατηγοριών) παραμένει προτεραίος όσο είναι ενεργός.
                    val currentEntry by navController.currentBackStackEntryAsState()
                    BackHandler(enabled = currentEntry != null && navController.previousBackStackEntry != null) {
                        safeBack()
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
                                // person = σε ποιον γράφεται· -1 = «στον πρώτο που δεν παρήγγειλε»,
                                // δηλαδή ό,τι έκανε πάντα το ΠΡΟΣΘΗΚΗ ΑΤΟΜΟΥ.
                                onAddMore = { person -> safeNavigate("menu/$table?person=$person") },
                                onBack = { safeBack() },
                            )
                        }
                        composable(
                            "menu/{table}?person={person}",
                            arguments = listOf(
                                navArgument("table") { type = NavType.IntType },
                                navArgument("person") { type = NavType.IntType; defaultValue = -1 },
                            ),
                        ) { backStackEntry ->
                            val table = backStackEntry.arguments?.getInt("table") ?: 1
                            MenuScreen(
                                prefs = prefs,
                                table = table,
                                startPerson = backStackEntry.arguments?.getInt("person") ?: -1,
                                onDone = { safeBack() },
                                // Τέλος παραγγελίας για όλα τα άτομα: πίσω στην αρχική με τα τραπέζια,
                                // όχι στην καρτέλα του τραπεζιού — ο σερβιτόρος πάει στο επόμενο τραπέζι.
                                onFinished = {
                                    debounced {
                                        navController.popBackStack("tables", inclusive = false)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
