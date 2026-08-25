package com.kemalcetin.aialarm.ui.aipreview

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.kemalcetin.aialarm.feature.assistant.network.PlanAlarm
import com.kemalcetin.aialarm.ui.common.formatTime
import com.kemalcetin.aialarm.ui.components.PhPrimaryButton
import com.kemalcetin.aialarm.ui.theme.PhRadius
import com.kemalcetin.aialarm.ui.theme.PhSpacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Structured preview of an interpreted natural-language request. NOT a chatbot.
 * Shows the concrete quick alarm OR the computed goal plan, then lets the user
 * confirm. Applying a quick alarm never schedules; CREATE PLAN is the explicit
 * confirmation that schedules the enabled plan alarms.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPreviewScreen(
    container: AppContainer,
    text: String,
    onBack: () -> Unit,
    onApply: (AiPreviewResult) -> Unit,
    onPlanCreated: () -> Unit
) {
    val viewModel: AiPreviewViewModel = viewModel(factory = AiPreviewViewModel.factory(container, text))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.planCreated.collect { onPlanCreated() }
    }

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
                state.loading || state.creating -> {
                    Box(Modifier.fillMaxWidth().padding(vertical = PhSpacing.section), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null -> {
                    PreviewMessageCard(text = stringResource(R.string.ai_parse_error))
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
                    val question = (state.result as AlarmInterpretResult.NeedsClarification)
                    ClarificationCard(
                        question = question.message,
                        input = state.clarificationInput,
                        onInputChange = viewModel::onClarificationInputChange,
                        onSubmit = viewModel::submitClarification
                    )
                    Spacer(Modifier.height(PhSpacing.lg))
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(PhRadius.button)
                    ) {
                        Text(stringResource(R.string.edit_request))
                    }
                }
                state.result is AlarmInterpretResult.GoalPlan -> {
                    val plan = state.result as AlarmInterpretResult.GoalPlan
                    GoalPlanCard(
                        plan = plan,
                        enabledIndices = state.planAlarmEnabled,
                        onToggle = viewModel::togglePlanAlarm
                    )
                    Spacer(Modifier.height(PhSpacing.section))
                    PhPrimaryButton(
                        text = stringResource(R.string.create_plan),
                        onClick = viewModel::createPlan,
                        enabled = state.planAlarmEnabled.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(PhSpacing.sm))
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(PhRadius.button)
                    ) {
                        Text(stringResource(R.string.edit_request))
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
private fun ClarificationCard(
    question: String,
    input: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PhRadius.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(PhSpacing.lg)) {
            Text(question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(PhSpacing.md))
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = { Text(stringResource(R.string.clarification_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(PhRadius.button),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(PhSpacing.md))
            PhPrimaryButton(
                text = stringResource(R.string.send),
                onClick = onSubmit,
                enabled = input.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun GoalPlanCard(
    plan: AlarmInterpretResult.GoalPlan,
    enabledIndices: Set<Int>,
    onToggle: (Int) -> Unit
) {
    val gradient = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PhRadius.cardHero),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(PhSpacing.lg)) {
            // Header
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(PhRadius.card))
                    .background(gradient, RoundedCornerShape(PhRadius.card))
                    .padding(PhSpacing.lg)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.plan_header),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(PhSpacing.xs))
                    Text(
                        text = plan.destinationLabel.ifBlank { stringResource(R.string.arrive_at) },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = formatHm(plan.targetTime),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Spacer(Modifier.height(PhSpacing.lg))

            // Schedule rows
            PlanFactRow(stringResource(R.string.sleep_target), formatHm(plan.sleepStartTime))
            PlanFactRow(stringResource(R.string.wake), formatHm(plan.wakeTime))
            PlanFactRow(stringResource(R.string.leave_by), formatHm(plan.leaveByTime))

            if (plan.sleepShortfallMinutes > 0) {
                Spacer(Modifier.height(PhSpacing.sm))
                Text(
                    text = stringResource(R.string.sleep_shortfall_notice, plan.sleepShortfallMinutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(PhSpacing.lg))
            Text(
                text = stringResource(R.string.assumptions),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(PhSpacing.xs))
            val a = plan.assumptions
            AssumptionRow(stringResource(R.string.sleep_target), "${a.sleepMinutes / 60} ${stringResource(R.string.hours)}")
            AssumptionRow(stringResource(R.string.preparation), "${a.preparationMinutes} ${stringResource(R.string.minutes_short)}")
            AssumptionRow(
                stringResource(R.string.commute),
                a.commuteMinutes?.let { "$it ${stringResource(R.string.minutes_short)}" }
                    ?: stringResource(R.string.not_set)
            )
            AssumptionRow(stringResource(R.string.buffer), "${a.bufferMinutes} ${stringResource(R.string.minutes_short)}")

            Spacer(Modifier.height(PhSpacing.lg))
            Text(
                text = stringResource(R.string.alarms),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(PhSpacing.xs))
            plan.alarms.forEachIndexed { index, alarm ->
                PlanAlarmRow(
                    alarm = alarm,
                    checked = index in enabledIndices,
                    onToggle = { onToggle(index) }
                )
            }
        }
    }
}

@Composable
private fun PlanFactRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = PhSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AssumptionRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = PhSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PlanAlarmRow(
    alarm: PlanAlarm,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PhRadius.card))
            .clickable(onClick = onToggle)
            .padding(vertical = PhSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(PhSpacing.sm))
        Text(
            text = formatHm(alarm.time),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(PhSpacing.md))
        Text(
            text = alarmRoleLabel(alarm.role, alarm.label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun alarmRoleLabel(role: String, fallback: String): String = when (role) {
    "gentle" -> stringResource(R.string.gentle_wake_up)
    "backup" -> stringResource(R.string.backup_alarm)
    else -> fallback.ifBlank { stringResource(R.string.wake) }
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
            }
        }
    }
}

private fun formatHm(time: String): String {
    val parts = time.split(":").map { it.toIntOrNull() }
    val h = parts.getOrNull(0) ?: return time
    val m = parts.getOrNull(1) ?: return time
    return formatTime(h, m)
}
