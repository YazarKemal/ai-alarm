package com.kemalcetin.aialarm.ui.aipreview

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.kemalcetin.aialarm.feature.assistant.network.AlarmInterpretResult
import com.kemalcetin.aialarm.ui.common.formatTime
import com.kemalcetin.aialarm.ui.components.PhPrimaryButton
import com.kemalcetin.aialarm.ui.theme.PhRadius
import com.kemalcetin.aialarm.ui.theme.PhSpacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Structured preview of an interpreted natural-language alarm. NOT a chatbot.
 * Shows the concrete time, date/tomorrow, repeat days and label, then lets the
 * user copy it into the editor with APPLY TO ALARM. Applying never schedules.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPreviewScreen(
    container: AppContainer,
    text: String,
    onBack: () -> Unit,
    onApply: (AiPreviewResult) -> Unit
) {
    val viewModel: AiPreviewViewModel = viewModel(factory = AiPreviewViewModel.factory(container, text))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(PhSpacing.sm))
                        Text(stringResource(R.string.ai_preview), fontWeight = FontWeight.SemiBold)
                    }
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
                .padding(horizontal = PhSpacing.lg)
        ) {
            Spacer(Modifier.height(PhSpacing.lg))
            Text(
                text = "“$text”",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3
            )
            Spacer(Modifier.height(PhSpacing.lg))

            when {
                state.loading -> {
                    Box(Modifier.fillMaxWidth().padding(vertical = PhSpacing.section), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null -> {
                    PreviewMessageCard(text = state.error.orEmpty())
                    Spacer(Modifier.height(PhSpacing.lg))
                    OutlinedButton(
                        onClick = viewModel::retry,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(PhRadius.button)
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                }
                state.result is AlarmInterpretResult.NeedsClarification -> {
                    val question = (state.result as AlarmInterpretResult.NeedsClarification).message
                    PreviewMessageCard(text = question)
                    Spacer(Modifier.height(PhSpacing.lg))
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(PhRadius.button)
                    ) {
                        Text(stringResource(R.string.refine_request))
                    }
                }
                state.result is AlarmInterpretResult.Alarm -> {
                    val alarm = state.result as AlarmInterpretResult.Alarm
                    AlarmPreviewCard(
                        hour = alarm.hour,
                        minute = alarm.minute,
                        date = alarm.date,
                        repeatDays = alarm.repeatDays,
                        label = alarm.label
                    )
                    Spacer(Modifier.height(PhSpacing.section))
                    PhPrimaryButton(
                        text = stringResource(R.string.apply_to_alarm),
                        onClick = {
                            onApply(
                                AiPreviewResult(
                                    hour = alarm.hour,
                                    minute = alarm.minute,
                                    date = alarm.date,
                                    repeatDays = alarm.repeatDays,
                                    label = alarm.label
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PreviewMessageCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PhRadius.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(PhSpacing.lg)) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(PhSpacing.sm))
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun AlarmPreviewCard(
    hour: Int,
    minute: Int,
    date: LocalDate?,
    repeatDays: Set<DayOfWeek>,
    label: String
) {
    val gradient = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PhRadius.cardHero),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(PhRadius.cardHero))
                .background(gradient, RoundedCornerShape(PhRadius.cardHero))
        ) {
            Column(
                Modifier.fillMaxWidth().padding(PhSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = formatTime(hour, minute),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.height(PhSpacing.sm))
                Text(
                    text = when {
                        date != null -> when (date) {
                            LocalDate.now() -> stringResource(R.string.today)
                            LocalDate.now().plusDays(1) -> stringResource(R.string.tomorrow)
                            else -> date.format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
                        }
                        repeatDays.isNotEmpty() -> repeatDays.toList()
                            .sortedBy { it.value }
                            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
                        else -> stringResource(R.string.one_time)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f)
                )
                if (label.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                    )
                }
                if (repeatDays.isNotEmpty()) {
                    Spacer(Modifier.height(PhSpacing.md))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeatDays.toList().sortedBy { it.value }.forEach { day ->
                            Text(
                                text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}
