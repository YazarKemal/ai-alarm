package com.kemalcetin.aialarm.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kemalcetin.aialarm.R
import com.kemalcetin.aialarm.core.alarm.NextAlarmCalculator
import com.kemalcetin.aialarm.di.AppContainer
import com.kemalcetin.aialarm.domain.model.Alarm
import com.kemalcetin.aialarm.feature.assistant.learning.ExpectedSlot
import com.kemalcetin.aialarm.ui.clock.ClockWidget
import com.kemalcetin.aialarm.ui.common.formatRepeatDays
import com.kemalcetin.aialarm.ui.common.formatTime
import com.kemalcetin.aialarm.ui.components.PhAiSuggestionCard
import com.kemalcetin.aialarm.ui.components.PhAlarmCard
import com.kemalcetin.aialarm.ui.components.PhTopBar
import com.kemalcetin.aialarm.ui.theme.PhRadius
import com.kemalcetin.aialarm.ui.theme.PhSpacing
import java.time.DayOfWeek
import java.time.ZonedDateTime

@Composable
fun HomeScreen(
    container: AppContainer,
    onAddAlarm: () -> Unit,
    onEditAlarm: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onSetSuggestion: (ExpectedSlot) -> Unit
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissions() }

    LifecycleResumeEffect(Unit) {
        viewModel.rescheduleAllEnabled()
        onPauseOrDispose { }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddAlarm,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_alarm_desc))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {
                PhTopBar(
                    title = stringResource(R.string.home_title),
                    onSettings = onOpenSettings
                )
            }

            item {
                ClockCard(
                    clock = uiState.clockStyle,
                    nextAlarm = uiState.nextAlarm
                )
            }

            uiState.aiSuggestion?.let { slot ->
                item {
                    PhAiSuggestionCard(
                        message = suggestionMessage(slot),
                        onSetAlarm = { onSetSuggestion(slot) },
                        onIgnore = { viewModel.dismissSuggestion(slot) },
                        modifier = Modifier.padding(horizontal = PhSpacing.lg, vertical = PhSpacing.sm)
                    )
                }
            }

            item {
                PermissionBanners(
                    permissions = uiState.permissions,
                    onRequestNotifications = {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    onOpenExactAlarmSettings = {
                        container.exactAlarmPermissionManager.openSettings()?.let(context::startActivity)
                    },
                    onOpenFullScreenSettings = {
                        container.fullScreenIntentPermissionManager.openSettings()?.let(context::startActivity)
                    }
                )
            }

            item {
                Text(
                    text = stringResource(R.string.alarms_section),
                    modifier = Modifier.padding(start = PhSpacing.xl, top = PhSpacing.section, bottom = PhSpacing.sm),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (uiState.alarms.isEmpty()) {
                item { EmptyAlarms(onAddAlarm = onAddAlarm) }
            } else {
                items(uiState.alarms, key = { it.id }) { alarm ->
                    PhAlarmCard(
                        alarm = alarm,
                        onClick = { onEditAlarm(alarm.id) },
                        onToggle = { enabled -> viewModel.setAlarmEnabled(alarm, enabled) },
                        modifier = Modifier.padding(horizontal = PhSpacing.lg, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ClockCard(
    clock: com.kemalcetin.aialarm.ui.clock.ClockStyle,
    nextAlarm: ZonedDateTime?
) {
    val todayText = stringResource(R.string.today)
    val tomorrowText = stringResource(R.string.tomorrow)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PhSpacing.lg, vertical = PhSpacing.sm),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(PhRadius.cardHero),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = PhSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ClockWidget(
                style = clock,
                contentColor = MaterialTheme.colorScheme.onSurface,
                nextAlarmText = nextAlarm?.let { nextAlarmSummary(it, todayText, tomorrowText) }
            )
        }
    }
}

@Composable
private fun PermissionBanners(
    permissions: PermissionStatus,
    onRequestNotifications: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenFullScreenSettings: () -> Unit
) {
    if (!permissions.notificationsGranted) {
        PermissionBanner(stringResource(R.string.notification_banner)) {
            TextButton(onClick = onRequestNotifications) { Text(stringResource(R.string.allow)) }
        }
    }
    if (!permissions.canScheduleExact) {
        PermissionBanner(stringResource(R.string.exact_alarm_banner)) {
            TextButton(onClick = onOpenExactAlarmSettings) { Text(stringResource(R.string.open_settings)) }
        }
    }
    if (!permissions.fullScreenAvailable) {
        PermissionBanner(stringResource(R.string.fullscreen_banner)) {
            TextButton(onClick = onOpenFullScreenSettings) { Text(stringResource(R.string.open_settings)) }
        }
    }
}

@Composable
private fun PermissionBanner(message: String, action: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PhSpacing.lg, vertical = 6.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(PhRadius.card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = PhSpacing.lg, end = PhSpacing.sm, top = PhSpacing.sm, bottom = PhSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            action()
        }
    }
}

@Composable
private fun EmptyAlarms(onAddAlarm: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Alarm,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(PhSpacing.lg))
            Text(
                text = stringResource(R.string.no_alarm_yet),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(PhSpacing.xs))
            Text(
                text = stringResource(R.string.no_alarm_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(PhSpacing.sm))
            TextButton(onClick = onAddAlarm) {
                Text(stringResource(R.string.add_alarm), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun nextAlarmSummary(next: ZonedDateTime, todayText: String, tomorrowText: String): String {
    val today = ZonedDateTime.now().toLocalDate()
    val label = when (next.toLocalDate()) {
        today -> todayText
        today.plusDays(1) -> tomorrowText
        else -> "${next.monthValue}/${next.dayOfMonth}"
    }
    return "$label · ${formatTime(next.hour, next.minute)}"
}

private fun suggestionMessage(slot: ExpectedSlot): String {
    val days = slot.repeatDays
    val whenText = when {
        days.isEmpty() -> "at ${formatTime(slot.hour, slot.minute)}"
        days.size >= 5 -> "a ${formatTime(slot.hour, slot.minute)} alarm on weekdays"
        else -> "a ${formatTime(slot.hour, slot.minute)} alarm on ${formatRepeatDays(days)}"
    }
    return "You usually use $whenText. No alarm is set for tomorrow."
}
