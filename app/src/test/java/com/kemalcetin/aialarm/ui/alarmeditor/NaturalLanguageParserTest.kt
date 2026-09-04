package com.kemalcetin.aialarm.ui.alarmeditor

import com.kemalcetin.aialarm.ui.alarmeditor.NaturalLanguageParser.Parsed
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalLanguageParserTest {

    @Test
    fun `parses 24h time with no days`() {
        val result = NaturalLanguageParser.parse("wake me at 07:30")!!
        assertEquals(7, result.hour)
        assertEquals(30, result.minute)
        assertTrue(result.repeatDays.isEmpty())
    }

    @Test
    fun `maps english weekday words to DayOfWeek`() {
        val result = NaturalLanguageParser.parse("alarm on monday, tuesday and friday at 7:00")!!
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.FRIDAY), result.repeatDays)
    }

    @Test
    fun `maps turkish weekday words to DayOfWeek`() {
        val result = NaturalLanguageParser.parse("pazartesi ve çarşamba 06:15")!!
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), result.repeatDays)
        assertEquals(6, result.hour)
        assertEquals(15, result.minute)
    }

    @Test
    fun `rejects minutes out of range`() {
        assertNull(NaturalLanguageParser.parse("alarm at 07:75"))
    }

    @Test
    fun `returns null when no time present`() {
        assertNull(NaturalLanguageParser.parse("remind me in the morning"))
    }

    @Test
    fun `blank input returns null`() {
        assertNull(NaturalLanguageParser.parse("   "))
    }
}
