package com.maslarski.sudoku.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.maslarski.sudoku.domain.model.Difficulty
import com.maslarski.sudoku.domain.model.GridSize
import com.maslarski.sudoku.ui.game.GameScreen
import com.maslarski.sudoku.ui.home.HomeScreen
import com.maslarski.sudoku.ui.leaderboard.LeaderboardScreen
import com.maslarski.sudoku.ui.store.StoreScreen

object Routes {
    const val HOME = "home"
    const val GAME = "game/{gridSize}"
    const val LEADERBOARD = "leaderboard?gridSize={gridSize}&difficulty={difficulty}"
    const val STORE = "store"

    fun game(gridSize: GridSize) = "game/${gridSize.size}"
    fun leaderboard(gridSize: GridSize? = null, difficulty: Difficulty? = null) =
        "leaderboard?gridSize=${gridSize?.size ?: 9}&difficulty=${difficulty?.name ?: Difficulty.EASY.name}"
}

@Composable
fun SudokuNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenGame = { gridSize -> navController.navigate(Routes.game(gridSize)) },
                onOpenLeaderboard = { navController.navigate(Routes.leaderboard()) },
                onOpenStore = { navController.navigate(Routes.STORE) },
            )
        }
        composable(
            route = Routes.GAME,
            arguments = listOf(navArgument("gridSize") { type = NavType.IntType }),
        ) {
            GameScreen(
                onBack = { navController.popBackStack() },
                onOpenStore = { navController.navigate(Routes.STORE) },
                onOpenLeaderboard = { g, d -> navController.navigate(Routes.leaderboard(g, d)) },
                onNewGame = { navController.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
        composable(
            route = Routes.LEADERBOARD,
            arguments = listOf(
                navArgument("gridSize") { type = NavType.IntType; defaultValue = 9 },
                navArgument("difficulty") { type = NavType.StringType; defaultValue = Difficulty.EASY.name },
            ),
        ) {
            LeaderboardScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.STORE) {
            StoreScreen(onBack = { navController.popBackStack() })
        }
    }
}
