package com.kemalcetin.aialarm.ui.alarmeditor

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kemalcetin.aialarm.R
import com.kemalcetin.aialarm.di.AppContainer
import com.kemalcetin.aialarm.domain.model.Alarm
import com.kemalcetin.aialarm.ui.common.SectionLabel
import com.kemalcetin.aialarm.ui.common.formatTime
import com.kemalcetin.aialarm.ui.components.PhDayChip
import com.kemalcetin.aialarm.ui.components.PhPrimaryButton
import com.kemalcetin.aialarm.ui.components.PhSettingsRow
import com.kemalcetin.aialarm.ui.components.PhSwitch
import com.kemalcetin.aialarm.ui.theme.PhRadius
import com.kemalcetin.aialarm.ui.theme.PhSize
import com.kemalcetin.aialarm.ui.theme.PhSpacing
import java.time.DayOfWeek
import java.time.Instant
import java.time.format.TextStyle
import java.time.ZonedDateTime
import java.util.Locale

private val repeatDayOptions = listOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
)

private val snoozeOptions = listOf(5, 10, 15)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorScreen(
    container: AppContainer,
    alarmId: Long,
    onDone: () -> Unit,
    prefillHour: Int? = null,
    prefillMinute: Int? = null,
    prefillDays: Set<DayOfWeek>? = null
) {
    val viewModel: AlarmEditorViewModel = viewModel(
        factory = AlarmEditorViewModel.factory(
            container = container,
            alarmId = alarmId,
            prefillHour = prefillHour,
            prefillMinute = prefillMinute,
            prefillDays = prefillDays
        )
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showTimePicker by remember { mutableStateOf(false) }
    var aiExpanded by remember { mutableStateOf(false) }
    var aiInput by remember { mutableStateOf("") }

    val nextOccurrence = remember(uiState.hour, uiState.minute, uiState.repeatDays) {
        container.nextAlarmCalculator.nextTrigger(
            Alarm(
                id = 0L,
                hour = uiState.hour,
                minute = uiState.minute,
                label = "",
                enabled = true,
                repeatDays = uiState.repeatDays,
                vibrate = true,
                soundUri = null,
                snoozeMinutes = uiState.snoozeMinutes,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            ),
            ZonedDateTime.now()
        )
    }

    LaunchedEffect(Unit) {
        viewModel.saved.collect { onDone() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(if (uiState.isEdit) R.string.edit_alarm else R.string.new_alarm),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_desc))
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
            // Hero time card
            TimeHeroCard(
                hour = uiState.hour,
                minute = uiState.minute,
                nextOccurrence = nextOccurrence,
                onClick = { showTimePicker = true }
            )

            Spacer(Modifier.height(PhSpacing.lg))

            // AI natural-language row (collapsed)
            AiNaturalLanguageCard(
                expanded = aiExpanded,
                input = aiInput,
                onToggle = { aiExpanded = !aiExpanded },
                onInputChange = { aiInput = it },
                onApply = {
                    viewModel.applyNaturalLanguage(aiInput)
                    aiExpanded = false
                    aiInput = ""
                }
            )

            Spacer(Modifier.height(PhSpacing.lg))

            // Repeat
            SectionLabel(stringResource(R.string.repeat).uppercase(), Modifier.padding(horizontal = PhSpacing.xl))
            Spacer(Modifier.height(PhSpacing.sm))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PhSpacing.lg),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                repeatDayOptions.forEach { day ->
                    PhDayChip(
                        day = day,
                        selected = day in uiState.repeatDays,
                        onClick = { viewModel.toggleDay(day) }
                    )
                }
            }

            Spacer(Modifier.height(PhSpacing.lg))

            // Label
            SectionLabel(stringResource(R.string.label).uppercase(), Modifier.padding(horizontal = PhSpacing.xl))
            Spacer(Modifier.height(PhSpacing.sm))
            OutlinedTextField(
                value = uiState.label,
                onValueChange = viewModel::setLabel,
                label = { Text(stringResource(R.string.alarm_label_hint)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(PhRadius.button),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PhSpacing.lg)
            )

            Spacer(Modifier.height(PhSpacing.lg))

            // Options card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PhSpacing.lg),
                shape = RoundedCornerShape(PhRadius.card),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                PhSettingsRow(
                    icon = Icons.Outlined.Vibration,
                    title = stringResource(R.string.vibration),
                    subtitle = stringResource(R.string.vibration_subtitle)
                ) {
                    PhSwitch(checked = uiState.vibrate, onCheckedChange = viewModel::setVibrate)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PhSpacing.md)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                SnoozeRow(
                    snoozeMinutes = uiState.snoozeMinutes,
                    onSelect = viewModel::setSnooze
                )
            }

            if (uiState.isEdit) {
                Spacer(Modifier.height(PhSpacing.lg))
                OutlinedButton(
                    onClick = { viewModel.delete() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PhSpacing.lg)
                        .height(52.dp),
                    shape = RoundedCornerShape(PhRadius.button)
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.delete_alarm), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(PhSpacing.section))

            PhPrimaryButton(
                text = stringResource(R.string.save_alarm),
                onClick = { viewModel.save() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PhSpacing.lg)
            )

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = uiState.hour,
            initialMinute = uiState.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setHour(timePickerState.hour)
                    viewModel.setMinute(timePickerState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = timePickerState) }
        )
    }
}

@Composable
private fun TimeHeroCard(
    hour: Int,
    minute: Int,
    nextOccurrence: ZonedDateTime?,
    onClick: () -> Unit
) {
    val gradient = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary
        )
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PhSpacing.lg)
            .clip(RoundedCornerShape(PhRadius.cardHero))
            .clickable(onClick = onClick)
            .background(gradient, RoundedCornerShape(PhRadius.cardHero))
            .padding(vertical = PhSpacing.xl),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatTime(hour, minute),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.height(4.dp))
            if (nextOccurrence != null) {
                val day = nextOccurrence.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                Text(
                    text = "$day · ${formatTime(nextOccurrence.hour, nextOccurrence.minute)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f)
                )
            } else {
                Text(
                    text = "One time",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.time_tap_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun AiNaturalLanguageCard(
    expanded: Boolean,
    input: String,
    onToggle: () -> Unit,
    onInputChange: (String) -> Unit,
    onApply: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PhSpacing.lg),
        shape = RoundedCornerShape(PhRadius.card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = PhSpacing.md, vertical = PhSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(PhSize.iconTile)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(PhSpacing.lg))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.set_with_ai),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.set_with_ai_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
        }
        if (expanded) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = { Text(stringResource(R.string.set_with_ai_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PhSpacing.md),
                shape = RoundedCornerShape(PhRadius.button)
            )
            Spacer(Modifier.height(PhSpacing.sm))
            PhPrimaryButton(
                text = stringResource(R.string.apply_to_alarm),
                onClick = onApply,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PhSpacing.md)
                    .padding(bottom = PhSpacing.md)
            )
        }
    }
}

@Composable
private fun SnoozeRow(
    snoozeMinutes: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PhSpacing.md, vertical = PhSpacing.lg)
    ) {
        Box(
            Modifier
                .size(PhSize.iconTile)
                .background(
                    MaterialTheme.colorScheme.secondaryContainer,
                    RoundedCornerShape(PhRadius.tile)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Snooze,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(PhSpacing.lg))
        Text(
            text = stringResource(R.string.snooze_duration),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        snoozeOptions.forEach { minutes ->
            FilterChip(
                selected = snoozeMinutes == minutes,
                onClick = { onSelect(minutes) },
                label = { Text("$minutes ${stringResource(R.string.snooze_minutes)}") },
                shape = RoundedCornerShape(PhRadius.chip)
            )
        }
    }
}
