package com.pittapos.waiter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
            MaterialTheme {
                Surface(modifier = Modifier) {
                    val navController = rememberNavController()
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
                                onOpenTable = { table -> navController.navigate("tableDetail/$table") },
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
                                onAddMore = { navController.navigate("menu/$table") },
                                onBack = { navController.popBackStack() },
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
                                onDone = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }
}
