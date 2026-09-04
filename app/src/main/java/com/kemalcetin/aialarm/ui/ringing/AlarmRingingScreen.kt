package com.kemalcetin.aialarm.ui.ringing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kemalcetin.aialarm.R
import com.kemalcetin.aialarm.di.AppContainer
import com.kemalcetin.aialarm.ui.common.formatTime
import com.kemalcetin.aialarm.ui.theme.PhRadius
import com.kemalcetin.aialarm.ui.theme.PhSpacing
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val GOLD = Color(0xFFFFD700)
private val ON_GOLD = Color(0xFF050505)
private val BRAND_BLACK = Color(0xFF050505)

private const val HOLD_MS = 1500L

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

    var holdProgress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    var holdJob by remember { mutableStateOf<Job?>(null) }

    fun startHold() {
        holdJob = scope.launch {
            val start = System.currentTimeMillis()
            while (isActive) {
                val elapsed = System.currentTimeMillis() - start
                holdProgress = (elapsed.toFloat() / HOLD_MS).coerceIn(0f, 1f)
                if (holdProgress >= 1f) {
                    viewModel.dismiss()
                    onDismiss()
                    break
                }
                delay(16)
            }
        }
    }

    fun cancelHold() {
        holdJob?.cancel()
        holdJob = null
        holdProgress = 0f
    }

    val dateLabel = remember { currentDateLabel() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BRAND_BLACK)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PhSpacing.xl)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = formatTime(currentTime.hour, currentTime.minute),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = label.ifBlank { stringResource(R.string.alarm_default_label) },
                style = MaterialTheme.typography.headlineMedium,
                color = GOLD,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.6f)
            )

            Spacer(Modifier.height(56.dp))

            // Gold SNOOZE button
            Button(
                onClick = {
                    viewModel.snooze()
                    onSnooze()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PhSize64),
                shape = RoundedCornerShape(PhRadius.button),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GOLD,
                    contentColor = ON_GOLD
                )
            ) {
                Text(stringResource(R.string.snooze_action), fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(PhSpacing.md))

            // HOLD TO DISMISS — 1500ms, release cancels/resets, completion dismisses.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PhSize64)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                startHold()
                                val released = tryAwaitRelease()
                                if (released) cancelHold()
                            }
                        )
                    },
                shape = RoundedCornerShape(PhRadius.button),
                color = Color.Transparent,
                border = BorderStroke(1.dp, GOLD.copy(alpha = 0.7f))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LinearProgressIndicator(
                        progress = { holdProgress },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(4.dp),
                        color = GOLD,
                        trackColor = GOLD.copy(alpha = 0.15f)
                    )
                    Text(
                        text = stringResource(R.string.hold_to_dismiss),
                        color = GOLD,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private fun currentDateLabel(): String {
    val today = LocalDate.now()
    val day = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val month = today.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    return "$day, ${today.dayOfMonth} $month"
}

private val PhSize64 = 64.dp
