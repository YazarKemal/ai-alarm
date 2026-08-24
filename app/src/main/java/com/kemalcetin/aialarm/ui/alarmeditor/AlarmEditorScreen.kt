package com.kemalcetin.aialarm.ui.alarmeditor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kemalcetin.aialarm.di.AppContainer
import com.kemalcetin.aialarm.ui.common.SectionHeader
import com.kemalcetin.aialarm.ui.common.formatRepeatDays
import com.kemalcetin.aialarm.ui.common.formatTime
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private val repeatDayOptions = listOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
)

private val snoozeOptions = listOf(5, 10, 15)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlarmEditorScreen(
    container: AppContainer,
    alarmId: Long,
    onDone: () -> Unit
) {
    val viewModel: AlarmEditorViewModel = viewModel(
        factory = AlarmEditorViewModel.factory(container, alarmId)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.saved.collect { onDone() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEdit) "Edit alarm" else "New alarm") },
                actions = {
                    TextButton(onClick = { viewModel.save() }) { Text("Save") }
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
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = formatTime(uiState.hour, uiState.minute),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = uiState.label.ifBlank { "Alarm" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (uiState.repeatDays.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = formatRepeatDays(uiState.repeatDays),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            SectionHeader("ZAMAN")
            OutlinedButton(
                onClick = { showTimePicker = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = formatTime(uiState.hour, uiState.minute),
                    style = MaterialTheme.typography.displayMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }

            SectionHeader("İŞLEM")
            OutlinedTextField(
                value = uiState.label,
                onValueChange = viewModel::setLabel,
                label = { Text("Label") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(16.dp))
            Text(
                "Repeat",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(Modifier.padding(horizontal = 16.dp)) {
                repeatDayOptions.forEach { day ->
                    FilterChip(
                        selected = day in uiState.repeatDays,
                        onClick = { viewModel.toggleDay(day) },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                        modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text("Vibrate", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = uiState.vibrate, onCheckedChange = viewModel::setVibrate)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Snooze",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(Modifier.padding(horizontal = 16.dp)) {
                snoozeOptions.forEach { minutes ->
                    FilterChip(
                        selected = uiState.snoozeMinutes == minutes,
                        onClick = { viewModel.setSnooze(minutes) },
                        label = { Text("$minutes min") },
                        modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)
                    )
                }
            }

            if (uiState.isEdit) {
                Spacer(Modifier.height(32.dp))
                OutlinedButton(
                    onClick = { viewModel.delete() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))
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
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }
}
