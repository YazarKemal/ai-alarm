package com.kemalcetin.aialarm.ui.clock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kemalcetin.aialarm.ui.common.formatTime
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * A self-updating clock. Ticks once per second and renders the selected
 * [ClockStyle]. Presentation only — never touches alarm scheduling.
 */
@Composable
fun ClockWidget(
    style: ClockStyle,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onBackground,
    nextAlarmText: String? = null
) {
    val time = rememberCurrentTime()
    val date = remember { currentDateLabel() }

    if (style.isDigital) {
        when (style) {
            ClockStyle.DIGITAL_MINIMAL -> DigitalClock(time, date, heavy = false, contentColor, nextAlarmText)
            ClockStyle.DIGITAL_BOLD -> DigitalClock(time, date, heavy = true, contentColor, nextAlarmText)
            ClockStyle.DIGITAL_NIGHT -> DigitalNight(time, date, contentColor)
            else -> DigitalClock(time, date, heavy = false, contentColor, nextAlarmText)
        }
    } else {
        AnalogClock(
            time = time,
            style = style,
            modifier = modifier.size(240.dp)
        )
    }
}

@Composable
private fun rememberCurrentTime(): LocalTime {
    var time by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            time = LocalTime.now()
            val msIntoSecond = time.nano / 1_000_000
            delay((1000 - msIntoSecond).toLong())
        }
    }
    return time
}

private fun currentDateLabel(): String {
    val today = LocalDate.now()
    val day = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val month = today.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    return "$day, ${today.dayOfMonth} $month"
}

@Composable
private fun DigitalClock(
    time: LocalTime,
    date: String,
    heavy: Boolean,
    contentColor: Color,
    nextAlarmText: String?
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatTime(time.hour, time.minute),
            style = if (heavy) MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black)
            else MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
            color = contentColor,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = date,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        if (nextAlarmText != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = nextAlarmText,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DigitalNight(
    time: LocalTime,
    date: String,
    contentColor: Color
) {
    val surface = Color(0xFF050505)
    Column(
        modifier = Modifier
            .background(surface)
            .height(240.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        Text(
            text = formatTime(time.hour, time.minute),
            style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
            color = contentColor.copy(alpha = 0.92f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = date,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor.copy(alpha = 0.55f),
            textAlign = TextAlign.Center
        )
    }
}
