package com.kemalcetin.aialarm.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kemalcetin.aialarm.di.AppContainer
import com.kemalcetin.aialarm.ui.alarmeditor.AlarmEditorScreen
import com.kemalcetin.aialarm.ui.home.HomeScreen
import com.kemalcetin.aialarm.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val EDITOR = "editor"
    const val EDITOR_ARG_ALARM_ID = "alarmId"
    const val EDITOR_WITH_ID = "$EDITOR/{$EDITOR_ARG_ALARM_ID}"

    fun editor(alarmId: Long): String = "$EDITOR/$alarmId"
}

@Composable
fun AppNavigation(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                container = container,
                onAddAlarm = { navController.navigate(Routes.editor(-1L)) },
                onEditAlarm = { id -> navController.navigate(Routes.editor(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                container = container,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.EDITOR_WITH_ID,
            arguments = listOf(
                navArgument(Routes.EDITOR_ARG_ALARM_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val alarmId = backStackEntry.arguments?.getLong(Routes.EDITOR_ARG_ALARM_ID) ?: -1L
            AlarmEditorScreen(
                container = container,
                alarmId = alarmId,
                onDone = { navController.popBackStack() }
            )
        }
    }
}
