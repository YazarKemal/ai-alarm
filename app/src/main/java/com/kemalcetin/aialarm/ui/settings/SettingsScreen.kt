package com.kemalcetin.aialarm.ui.settings

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kemalcetin.aialarm.di.AppContainer
import com.kemalcetin.aialarm.ui.common.SectionHeader

private val snoozeOptions = listOf(5, 10, 15)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            SectionHeader("ALARM")
            Text(
                "Default snooze",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(Modifier.padding(horizontal = 16.dp)) {
                snoozeOptions.forEach { minutes ->
                    FilterChip(
                        selected = uiState.defaultSnoozeMinutes == minutes,
                        onClick = { viewModel.setDefaultSnooze(minutes) },
                        label = { Text("$minutes min") },
                        modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)
                    )
                }
            }

            SectionHeader("AI ASSISTANT")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text("Auto-set forgotten alarms", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = uiState.autoCreateEnabled, onCheckedChange = viewModel::setAutoCreateEnabled)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "DeepSeek (bulut zeka): " +
                    if (uiState.deepSeekConfigured) "Açık" else "Kapalı",
                style = MaterialTheme.typography.bodyMedium,
                color = if (uiState.deepSeekConfigured) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (uiState.deepSeekConfigured) {
                    "DeepSeek, cihaz içi hesabın bulamadığı düzenleri bulmaya yardımcı olur."
                } else {
                    "DeepSeek anahtarı eklenmedi; asistan yalnızca cihaz içi hesapla çalışıyor."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
