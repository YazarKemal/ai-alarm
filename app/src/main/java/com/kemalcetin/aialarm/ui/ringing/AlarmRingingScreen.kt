package com.kemalcetin.aialarm.ui.ringing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kemalcetin.aialarm.di.AppContainer
import com.kemalcetin.aialarm.ui.common.formatTime

@Composable
fun AlarmRingingScreen(
    container: AppContainer,
    alarmId: Long,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    val viewModel: AlarmRingingViewModel = viewModel(
        factory = AlarmRingingViewModel.factory(container, alarmId)
    )
    val label by viewModel.label.collectAsStateWithLifecycle()
    val currentTime by viewModel.currentTime.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = formatTime(currentTime.hour, currentTime.minute),
            style = MaterialTheme.typography.displayLarge
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(48.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = {
                    viewModel.snooze()
                    onSnooze()
                },
                modifier = Modifier.weight(1f).height(64.dp)
            ) {
                Text("SNOOZE")
            }
            Button(
                onClick = {
                    viewModel.dismiss()
                    onDismiss()
                },
                modifier = Modifier.weight(1f).height(64.dp)
            ) {
                Text("DISMISS")
            }
        }
    }
}
