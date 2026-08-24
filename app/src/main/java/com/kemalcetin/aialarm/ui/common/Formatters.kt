package com.kemalcetin.aialarm.ui.common

import com.kemalcetin.aialarm.feature.assistant.learning.ExpectedSlot
import com.kemalcetin.aialarm.feature.assistant.model.AlarmEventType
import com.kemalcetin.aialarm.feature.assistant.model.DismissIntent
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

fun formatTime(hour: Int, minute: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", hour, minute)

private val orderedDays = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY
)

fun formatRepeatDays(days: Set<DayOfWeek>): String =
    orderedDays.filter { it in days }
        .joinToString(" ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }

/** e.g. "07:00 · her gün" or "07:00 · Pa Sa Ça" */
fun formatExpectedSlot(slot: ExpectedSlot): String {
    val days = if (slot.repeatDays == DayOfWeek.entries.toSet()) {
        "her gün"
    } else {
        formatRepeatDays(slot.repeatDays)
    }
    return "${formatTime(slot.hour, slot.minute)} · $days"
}

fun formatEventType(type: AlarmEventType): String = when (type) {
    AlarmEventType.FIRED -> "Çaldı"
    AlarmEventType.SNOOZED -> "Ertele"
    AlarmEventType.DISMISSED -> "Kapatıldı"
    AlarmEventType.AUTO_CREATED -> "Otomatik kuruldu"
}

fun formatDismissIntent(intent: DismissIntent): String = when (intent) {
    DismissIntent.WILLING -> "İsteyerek"
    DismissIntent.ACCIDENTAL -> "Kazara"
    DismissIntent.UNKNOWN -> "Belirsiz"
}
