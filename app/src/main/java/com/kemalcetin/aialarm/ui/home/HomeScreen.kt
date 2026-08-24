package com.kemalcetin.aialarm.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kemalcetin.aialarm.BuildConfig
import com.kemalcetin.aialarm.di.AppContainer
import com.kemalcetin.aialarm.domain.model.Alarm
import com.kemalcetin.aialarm.feature.assistant.learning.ExpectedSlot
import com.kemalcetin.aialarm.feature.assistant.model.AlarmEvent
import com.kemalcetin.aialarm.ui.common.SectionHeader
import com.kemalcetin.aialarm.ui.common.formatDismissIntent
import com.kemalcetin.aialarm.ui.common.formatEventType
import com.kemalcetin.aialarm.ui.common.formatExpectedSlot
import com.kemalcetin.aialarm.ui.common.formatRepeatDays
import com.kemalcetin.aialarm.ui.common.formatTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onAddAlarm: () -> Unit,
    onEditAlarm: (Long) -> Unit,
    onOpenSettings: () -> Unit
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
        topBar = {
            TopAppBar(
                title = { Text("AI Alarm") },
                actions = {
                    if (BuildConfig.DEBUG) {
                        IconButton(onClick = { viewModel.scheduleTestAlarm() }) {
                            Icon(Icons.Outlined.Timer, contentDescription = "Schedule test alarm")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddAlarm) {
                Icon(Icons.Filled.Add, contentDescription = "Add alarm")
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            SectionHeader("AI ASSISTANT")
            AiAssistantCard(
                autoCreateEnabled = uiState.aiAutoCreateEnabled,
                deepSeekConfigured = uiState.aiDeepSeekConfigured,
                slots = uiState.aiSlots,
                nextExpected = uiState.aiNextExpected,
                recentEvents = uiState.aiRecentEvents,
                lastAutoCreate = viewModel.formatAiLastAutoCreate(uiState.aiLastAutoCreateTime),
                onToggle = viewModel::setAiAutoCreateEnabled
            )
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

            SectionHeader("ALARMS")
            if (uiState.alarms.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Alarm,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Henüz alarm yok.\n+ ile alarm ekle.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(uiState.alarms, key = { it.id }) { alarm ->
                        AlarmRow(
                            alarm = alarm,
                            onClick = { onEditAlarm(alarm.id) },
                            onToggle = { enabled -> viewModel.setAlarmEnabled(alarm, enabled) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiAssistantCard(
    autoCreateEnabled: Boolean,
    deepSeekConfigured: Boolean,
    slots: List<ExpectedSlot>,
    nextExpected: String?,
    recentEvents: List<AlarmEvent>,
    lastAutoCreate: String,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "AI Assistant",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Son kurulum: $lastAutoCreate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Switch(checked = autoCreateEnabled, onCheckedChange = onToggle)
            }

            Spacer(Modifier.height(8.dp))
            Row {
                StatusBadge("Otomatik kurma", autoCreateEnabled)
                Spacer(Modifier.width(12.dp))
                StatusBadge("DeepSeek", deepSeekConfigured)
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Öğrenilen saatler",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(4.dp))
            if (slots.isEmpty()) {
                Text(
                    "Henüz öğrenilmedi — tekrarlı alarmlar kullandıkça görünür.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            } else {
                slots.forEach { slot ->
                    Text(
                        formatExpectedSlot(slot),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Sıradaki beklenen alarm",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                nextExpected ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Spacer(Modifier.height(12.dp))
            Text(
                "Son aktivite",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            if (recentEvents.isEmpty()) {
                Text(
                    "Henüz aktivite yok.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            } else {
                recentEvents.forEach { event ->
                    val intent = event.dismissIntent
                    Text(
                        buildString {
                            append(formatEventType(event.type))
                            append(" · ")
                            append(formatEventTime(event))
                            if (intent != null) {
                                append(" · ")
                                append(formatDismissIntent(intent))
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun formatEventTime(event: AlarmEvent): String {
    val at = event.occurredAt.atZone(ZoneId.systemDefault())
    return formatTime(at.hour, at.minute)
}

@Composable
private fun StatusBadge(label: String, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(
                    if (active) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                    CircleShape
                )
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$label: ${if (active) "Açık" else "Kapalı"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
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
        Banner("Notifications are disabled. Alarms may not show a full-screen alert.") {
            TextButton(onClick = onRequestNotifications) { Text("Allow") }
        }
    }
    if (!permissions.canScheduleExact) {
        Banner("Exact alarm permission is required for reliable alarms.") {
            TextButton(onClick = onOpenExactAlarmSettings) { Text("Open settings") }
        }
    }
    if (!permissions.fullScreenAvailable) {
        Banner("Full-screen alarm access is disabled.") {
            TextButton(onClick = onOpenFullScreenSettings) { Text("Open settings") }
        }
    }
}

@Composable
internal fun Banner(message: String, action: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
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
private fun AlarmRow(
    alarm: Alarm,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val emphasis = if (alarm.enabled) 1f else 0.5f
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Alarm,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = formatTime(alarm.hour, alarm.minute),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = emphasis)
                )
                Text(
                    text = alarm.label.ifBlank { "Alarm" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = emphasis)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (alarm.isOneTime) "Bir kez" else formatRepeatDays(alarm.repeatDays),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = emphasis)
                )
            }
            Switch(
                checked = alarm.enabled,
                onCheckedChange = onToggle
            )
        }
    }
}
