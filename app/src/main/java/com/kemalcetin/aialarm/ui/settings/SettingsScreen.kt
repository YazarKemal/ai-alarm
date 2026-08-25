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
