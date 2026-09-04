package com.kemalcetin.aialarm.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kemalcetin.aialarm.di.AppContainer
import com.kemalcetin.aialarm.ui.aipreview.AiPreviewScreen
import com.kemalcetin.aialarm.ui.aipreview.AiPreviewViewModel
import com.kemalcetin.aialarm.ui.alarmeditor.AlarmEditorScreen
import com.kemalcetin.aialarm.ui.clock.ClockStyleScreen
import com.kemalcetin.aialarm.ui.home.HomeScreen
import com.kemalcetin.aialarm.ui.settings.SettingsScreen
import java.time.DayOfWeek

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val CLOCK_STYLE = "clock_style"
    const val EDITOR = "editor"
    const val EDITOR_ARG_ALARM_ID = "alarmId"
    const val EDITOR_ARG_HOUR = "suggestHour"
    const val EDITOR_ARG_MINUTE = "suggestMinute"
    const val EDITOR_ARG_DAYS = "suggestDays"
    const val EDITOR_WITH_ID =
        "$EDITOR/{$EDITOR_ARG_ALARM_ID}?$EDITOR_ARG_HOUR={$EDITOR_ARG_HOUR}" +
            "&$EDITOR_ARG_MINUTE={$EDITOR_ARG_MINUTE}&$EDITOR_ARG_DAYS={$EDITOR_ARG_DAYS}"

    const val AI_PREVIEW = "ai_preview"
    const val AI_PREVIEW_ARG_TEXT = "text"
    const val AI_PREVIEW_ROUTE = "$AI_PREVIEW/{$AI_PREVIEW_ARG_TEXT}"

    fun editor(alarmId: Long): String = "$EDITOR/$alarmId"

    /** Editor route pre-filled from an AI suggestion (only meaningful for a new alarm). */
    fun editorSuggestion(hour: Int, minute: Int, days: Set<DayOfWeek>): String =
        "$EDITOR/-1?suggestHour=$hour&suggestMinute=$minute&suggestDays=" +
            days.joinToString(",") { it.value.toString() }

    /** AI Preview route carrying the user's natural-language description. */
    fun aiPreview(text: String): String = "$AI_PREVIEW/${Uri.encode(text)}"
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
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onSetSuggestion = { slot ->
                    navController.navigate(
                        Routes.editorSuggestion(slot.hour, slot.minute, slot.repeatDays)
                    )
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onOpenClockStyle = { navController.navigate(Routes.CLOCK_STYLE) }
            )
        }

        composable(Routes.CLOCK_STYLE) {
            ClockStyleScreen(
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
                },
                navArgument(Routes.EDITOR_ARG_HOUR) {
                    type = NavType.IntType
                    defaultValue = -1
                },
                navArgument(Routes.EDITOR_ARG_MINUTE) {
                    type = NavType.IntType
                    defaultValue = -1
                },
                navArgument(Routes.EDITOR_ARG_DAYS) {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            val alarmId = args?.getLong(Routes.EDITOR_ARG_ALARM_ID) ?: -1L
            val suggestHour = args?.getInt(Routes.EDITOR_ARG_HOUR).takeIf { it != -1 }
            val suggestMinute = args?.getInt(Routes.EDITOR_ARG_MINUTE).takeIf { it != -1 }
            val suggestDays = args?.getString(Routes.EDITOR_ARG_DAYS)
                ?.takeIf { it.isNotBlank() }
                ?.split(",")
                ?.mapNotNull { str -> DayOfWeek.entries.firstOrNull { it.value.toString() == str } }
                ?.toSet()
            AlarmEditorScreen(
                container = container,
                alarmId = alarmId,
                onDone = { navController.popBackStack() },
                onOpenAiPreview = { text ->
                    navController.navigate(Routes.aiPreview(text))
                },
                savedStateHandle = backStackEntry.savedStateHandle,
                prefillHour = suggestHour,
                prefillMinute = suggestMinute,
                prefillDays = suggestDays
            )
        }

        composable(
            route = Routes.AI_PREVIEW_ROUTE,
            arguments = listOf(
                navArgument(Routes.AI_PREVIEW_ARG_TEXT) {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val text = backStackEntry.arguments?.getString(Routes.AI_PREVIEW_ARG_TEXT) ?: ""
            AiPreviewScreen(
                container = container,
                text = text,
                onBack = { navController.popBackStack() },
                onApply = { result ->
                    // Return the preview to the editor and pop back. Applying never
                    // saves or schedules; the editor's SAVE is the only scheduler.
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(AiPreviewViewModel.AI_PREVIEW_RESULT_KEY, result)
                    navController.popBackStack()
                },
                // CREATE PLAN already scheduled the enabled alarms; return to Home
                // (clearing the editor that led here).
                onPlanCreated = { navController.popBackStack(Routes.HOME, inclusive = false) }
            )
        }
    }
}
