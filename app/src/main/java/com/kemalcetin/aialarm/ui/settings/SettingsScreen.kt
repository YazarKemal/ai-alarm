package com.kemalcetin.aialarm.ui.settings

import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kemalcetin.aialarm.R
import com.kemalcetin.aialarm.core.locale.AppLanguage
import com.kemalcetin.aialarm.core.planning.AiPlanningPreferences
import com.kemalcetin.aialarm.core.planning.WakePreference
import com.kemalcetin.aialarm.di.AppContainer
import com.kemalcetin.aialarm.ui.components.PhSettingsRow
import com.kemalcetin.aialarm.ui.components.PhSwitch
import com.kemalcetin.aialarm.ui.theme.PhRadius
import com.kemalcetin.aialarm.ui.theme.PhSpacing
import com.kemalcetin.aialarm.ui.theme.ThemeMode

private val snoozeOptions = listOf(5, 10, 15)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenClockStyle: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showPlanningDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // APPEARANCE
            SectionHeader(stringResource(R.string.appearance))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PhSpacing.lg),
                shape = RoundedCornerShape(PhRadius.card),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                PhSettingsRow(
                    icon = Icons.Outlined.Snooze,
                    title = stringResource(R.string.clock_style_title),
                    subtitle = stringResource(uiState.clockStyle.labelRes),
                    onClick = onOpenClockStyle
                ) {
                    Text(stringResource(R.string.status_allowed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                DividerLine()
                PhSettingsRow(
                    icon = Icons.Outlined.Circle,
                    title = stringResource(R.string.theme),
                    subtitle = stringResource(themeSubtitleRes(uiState.themeMode))
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(PhSpacing.xs)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = uiState.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(stringResource(themeLabelRes(mode))) },
                                shape = RoundedCornerShape(PhRadius.chip)
                            )
                        }
                    }
                }
                DividerLine()
                PhSettingsRow(
                    icon = Icons.Outlined.Circle,
                    title = stringResource(R.string.language),
                    subtitle = uiState.language.nativeName,
                    onClick = { showLanguageDialog = true }
                ) {}
            }

            Spacer(Modifier.height(PhSpacing.section))

            // ALARM DEFAULTS
            SectionHeader(stringResource(R.string.alarm_defaults))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PhSpacing.lg),
                shape = RoundedCornerShape(PhRadius.card),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                PhSettingsRow(
                    icon = Icons.Outlined.Snooze,
                    title = stringResource(R.string.default_snooze),
                    subtitle = stringResource(R.string.default_snooze_subtitle)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(PhSpacing.xs)) {
                        snoozeOptions.forEach { minutes ->
                            FilterChip(
                                selected = uiState.defaultSnoozeMinutes == minutes,
                                onClick = { viewModel.setDefaultSnooze(minutes) },
                                label = { Text("$minutes ${stringResource(R.string.snooze_minutes)}") },
                                shape = RoundedCornerShape(PhRadius.chip)
                            )
                        }
                    }
                }
                DividerLine()
                PhSettingsRow(
                    icon = Icons.Outlined.Circle,
                    title = stringResource(R.string.default_alarm_sound),
                    subtitle = stringResource(R.string.default_alarm_sound)
                ) {
                    Text(stringResource(R.string.one_time), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(PhSpacing.section))

            // PROMPTHAVEN AI
            SectionHeader(stringResource(R.string.ph_ai_section))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PhSpacing.lg),
                shape = RoundedCornerShape(PhRadius.card),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                PhSettingsRow(
                    icon = Icons.Outlined.AutoAwesome,
                    title = stringResource(R.string.smart_suggestions),
                    subtitle = stringResource(R.string.smart_suggestions_subtitle)
                ) {
                    PhSwitch(checked = uiState.smartSuggestionsEnabled, onCheckedChange = viewModel::setSmartSuggestionsEnabled)
                }
                DividerLine()
                PhSettingsRow(
                    icon = Icons.Outlined.AutoAwesome,
                    title = stringResource(R.string.ai_setup),
                    subtitle = stringResource(R.string.ai_setup_subtitle)
                ) {
                    Text(
                        stringResource(if (uiState.smartSuggestionsEnabled) R.string.ai_setup_enabled else R.string.ai_setup_disabled),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                DividerLine()
                PhSettingsRow(
                    icon = Icons.Outlined.AutoAwesome,
                    title = stringResource(R.string.planning_preferences),
                    subtitle = stringResource(R.string.planning_preferences_subtitle),
                    onClick = { showPlanningDialog = true }
                ) {
                    Text(stringResource(R.string.open), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(PhSpacing.section))

            // ALARM RELIABILITY
            SectionHeader(stringResource(R.string.alarm_reliability))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PhSpacing.lg),
                shape = RoundedCornerShape(PhRadius.card),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                ReliabilityRow(
                    icon = Icons.Outlined.NotificationsActive,
                    title = stringResource(R.string.exact_alarm_access),
                    ok = uiState.exactAlarmAllowed,
                    statusOk = stringResource(R.string.status_allowed),
                    statusBad = stringResource(R.string.status_action_needed),
                    onClick = { container.exactAlarmPermissionManager.openSettings()?.let(context::startActivity) }
                )
                DividerLine()
                ReliabilityRow(
                    icon = Icons.Outlined.NotificationsActive,
                    title = stringResource(R.string.notifications),
                    ok = uiState.notificationsGranted,
                    statusOk = stringResource(R.string.status_allowed),
                    statusBad = stringResource(R.string.status_action_needed),
                    onClick = null
                )
                DividerLine()
                ReliabilityRow(
                    icon = Icons.Outlined.NotificationsActive,
                    title = stringResource(R.string.full_screen_alarms),
                    ok = uiState.fullScreenAvailable,
                    statusOk = stringResource(R.string.status_allowed),
                    statusBad = stringResource(R.string.status_action_needed),
                    onClick = { container.fullScreenIntentPermissionManager.openSettings()?.let(context::startActivity) }
                )
                DividerLine()
                ReliabilityRow(
                    icon = Icons.Outlined.VerifiedUser,
                    title = stringResource(R.string.battery_optimization),
                    ok = uiState.batteryOptimized,
                    statusOk = stringResource(R.string.status_optimized),
                    statusBad = stringResource(R.string.status_recommended),
                    onClick = { container.batteryOptimizationManager.openSettings()?.let(context::startActivity) }
                )
            }

            Spacer(Modifier.height(PhSpacing.section))

            // ABOUT
            SectionHeader(stringResource(R.string.about))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PhSpacing.lg),
                shape = RoundedCornerShape(PhRadius.card),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                PhSettingsRow(
                    icon = Icons.Outlined.Circle,
                    title = stringResource(R.string.app_name),
                    subtitle = "v${uiState.appVersion}"
                ) {}
                DividerLine()
                PhSettingsRow(
                    icon = Icons.Outlined.Circle,
                    title = stringResource(R.string.privacy),
                    subtitle = ""
                ) {}
                DividerLine()
                PhSettingsRow(
                    icon = Icons.Outlined.Circle,
                    title = stringResource(R.string.open_source_licenses),
                    subtitle = ""
                ) {}
            }

            Spacer(Modifier.height(PhSpacing.lg))
            Text(
                text = stringResource(R.string.ecosystem_footer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = PhSpacing.lg),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(PhSpacing.lg))
        }
    }

    if (showLanguageDialog) {
        LanguagePickerDialog(
            current = uiState.language,
            onSelect = { lang ->
                if (lang != uiState.language) {
                    viewModel.setLanguage(lang)
                    // Recreate the single activity so its base context is rewrapped
                    // with the new locale (immediate, no device-settings required).
                    (context as? Activity)?.recreate()
                }
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showPlanningDialog) {
        PlanningPreferencesDialog(
            current = uiState.planningPreferences,
            onSave = { updated ->
                viewModel.setAiPlanningPreferences(updated)
                showPlanningDialog = false
            },
            onDismiss = { showPlanningDialog = false }
        )
    }
}

@Composable
private fun LanguagePickerDialog(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text(stringResource(R.string.language_dialog_title)) },
        text = {
            Column {
                AppLanguage.entries.forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(language) }
                            .padding(vertical = PhSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = language == current,
                            onClick = { onSelect(language) }
                        )
                        Spacer(Modifier.width(PhSpacing.md))
                        Text(
                            text = language.nativeName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    )
}

/**
 * Edits the on-device AI planning preferences used when the backend computes a
 * goal plan. All values stay local (DataStore); nothing is uploaded as a
 * profile. Save is explicit via the Save button.
 */
@Composable
private fun PlanningPreferencesDialog(
    current: AiPlanningPreferences,
    onSave: (AiPlanningPreferences) -> Unit,
    onDismiss: () -> Unit
) {
    // Local editable copies so Cancel leaves the stored prefs untouched.
    var sleepMinutes by remember { mutableStateOf(current.targetSleepMinutes) }
    var preparationMinutes by remember { mutableStateOf(current.preparationMinutes) }
    var commuteMinutes by remember { mutableStateOf(current.commuteMinutes) }
    var bufferMinutes by remember { mutableStateOf(current.bufferMinutes) }
    var wakePref by remember { mutableStateOf(current.wakePreference) }
    var preAlarmEnabled by remember { mutableStateOf(current.preAlarmEnabled) }
    var preAlarmMinutes by remember { mutableStateOf(current.preAlarmMinutes) }
    var backupEnabled by remember { mutableStateOf(current.backupAlarmEnabled) }
    var backupMinutes by remember { mutableStateOf(current.backupAlarmMinutes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.planning_preferences)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                MinutesField(
                    label = stringResource(R.string.sleep_target),
                    value = sleepMinutes,
                    onValueChange = { sleepMinutes = it },
                    step = 30
                )
                MinutesField(
                    label = stringResource(R.string.preparation),
                    value = preparationMinutes,
                    onValueChange = { preparationMinutes = it },
                    step = 5
                )
                MinutesField(
                    label = stringResource(R.string.buffer),
                    value = bufferMinutes,
                    onValueChange = { bufferMinutes = it },
                    step = 5
                )

                Spacer(Modifier.height(PhSpacing.md))

                // Typical commute — supports an explicit "Not set".
                Text(stringResource(R.string.commute), style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PhSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = commuteMinutes == null,
                        onClick = { commuteMinutes = null },
                        label = { Text(stringResource(R.string.not_set)) },
                        shape = RoundedCornerShape(PhRadius.chip)
                    )
                    MinuteStepper(value = commuteMinutes ?: 30, onChange = { commuteMinutes = it })
                }

                Spacer(Modifier.height(PhSpacing.md))

                Text(stringResource(R.string.wake_preference), style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PhSpacing.xs)
                ) {
                    WakePreference.entries.forEach { pref ->
                        FilterChip(
                            selected = wakePref == pref,
                            onClick = { wakePref = pref },
                            label = { Text(stringResource(wakePrefLabel(pref))) },
                            shape = RoundedCornerShape(PhRadius.chip)
                        )
                    }
                }

                Spacer(Modifier.height(PhSpacing.md))

                ToggleMinutesRow(
                    label = stringResource(R.string.pre_alarm),
                    checked = preAlarmEnabled,
                    onCheckedChange = { preAlarmEnabled = it },
                    minutes = preAlarmMinutes,
                    onMinutesChange = { preAlarmMinutes = it },
                    step = 5
                )
                ToggleMinutesRow(
                    label = stringResource(R.string.backup_alarm),
                    checked = backupEnabled,
                    onCheckedChange = { backupEnabled = it },
                    minutes = backupMinutes,
                    onMinutesChange = { backupMinutes = it },
                    step = 5
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    AiPlanningPreferences(
                        targetSleepMinutes = sleepMinutes.coerceIn(240, 720),
                        preparationMinutes = preparationMinutes.coerceIn(0, 360),
                        commuteMinutes = commuteMinutes?.coerceIn(0, 480),
                        bufferMinutes = bufferMinutes.coerceIn(0, 180),
                        wakePreference = wakePref,
                        preAlarmEnabled = preAlarmEnabled,
                        preAlarmMinutes = preAlarmMinutes.coerceIn(0, 60),
                        backupAlarmEnabled = backupEnabled,
                        backupAlarmMinutes = backupMinutes.coerceIn(0, 60)
                    )
                )
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/** Numeric input + +/- stepper for a minutes value. */
@Composable
private fun MinutesField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    step: Int
) {
    Column(Modifier.padding(vertical = PhSpacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(PhSpacing.xs))
        MinuteStepper(value = value, onChange = onValueChange, step = step)
    }
}

/** A compact minus / value / plus stepper for minute values. */
@Composable
private fun MinuteStepper(
    value: Int,
    onChange: (Int) -> Unit,
    step: Int = 5
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PhSpacing.sm)
    ) {
        IconButton(onClick = { onChange((value - step).coerceAtLeast(0)) }) {
            Icon(Icons.Outlined.Circle, contentDescription = stringResource(R.string.decrease))
        }
        Text("$value ${stringResource(R.string.minutes_short)}", style = MaterialTheme.typography.bodyMedium)
        IconButton(onClick = { onChange(value + step) }) {
            Icon(Icons.Outlined.Circle, contentDescription = stringResource(R.string.increase))
        }
    }
}

/** A toggle row (enable switch) plus an inline minute stepper. */
@Composable
private fun ToggleMinutesRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    step: Int
) {
    Column(Modifier.padding(vertical = PhSpacing.xs)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            PhSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
        if (checked) {
            Spacer(Modifier.height(PhSpacing.xs))
            MinuteStepper(value = minutes, onChange = onMinutesChange, step = step)
        }
    }
}

@StringRes
private fun wakePrefLabel(pref: WakePreference): Int = when (pref) {
    WakePreference.LATEST_POSSIBLE -> R.string.wake_pref_latest
    WakePreference.BALANCED -> R.string.wake_pref_balanced
    WakePreference.EARLY -> R.string.wake_pref_early
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = PhSpacing.xl, top = PhSpacing.md, bottom = PhSpacing.sm)
    )
}

@Composable
private fun DividerLine() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PhSpacing.md)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun ReliabilityRow(
    icon: ImageVector,
    title: String,
    ok: Boolean,
    statusOk: String,
    statusBad: String,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = PhSpacing.md, vertical = PhSpacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(PhSpacing.lg))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (ok) statusOk else statusBad,
            style = MaterialTheme.typography.bodySmall,
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}

@StringRes
private fun themeSubtitleRes(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.system_default
    ThemeMode.LIGHT -> R.string.light
    ThemeMode.DARK -> R.string.dark
}

@StringRes
private fun themeLabelRes(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.theme_auto
    ThemeMode.LIGHT -> R.string.light
    ThemeMode.DARK -> R.string.dark
}
