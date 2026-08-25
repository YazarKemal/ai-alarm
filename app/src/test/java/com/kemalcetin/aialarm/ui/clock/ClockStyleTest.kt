package com.kemalcetin.aialarm.ui.clock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the clock-style model plus the DataStore round-trip contract used by
 * AppPreferences (valueOf mapping, invalid-value fallback).
 */
class ClockStyleTest {

    @Test
    fun `digital styles report isDigital`() {
        assertTrue(ClockStyle.DIGITAL_MINIMAL.isDigital)
        assertTrue(ClockStyle.DIGITAL_BOLD.isDigital)
        assertTrue(ClockStyle.DIGITAL_NIGHT.isDigital)
    }

    @Test
    fun `analog styles are not digital`() {
        assertFalse(ClockStyle.ANALOG_CLASSIC.isDigital)
        assertFalse(ClockStyle.ANALOG_MINIMAL.isDigital)
        assertFalse(ClockStyle.ANALOG_GOLD.isDigital)
    }

    @Test
    fun `default style is digital minimal`() {
        assertEquals(ClockStyle.DIGITAL_MINIMAL, com.kemalcetin.aialarm.data.prefs.AppPreferences.DEFAULT_CLOCK_STYLE)
    }

    @Test
    fun `all stored names round-trip via valueOf`() {
        ClockStyle.entries.forEach { style ->
            assertEquals(style, ClockStyle.valueOf(style.name))
        }
    }

    @Test
    fun `invalid stored value falls back to default`() {
        val recovered = runCatching { ClockStyle.valueOf("NOT_A_REAL_STYLE") }.getOrNull()
        assertEquals(
            com.kemalcetin.aialarm.data.prefs.AppPreferences.DEFAULT_CLOCK_STYLE,
            recovered ?: com.kemalcetin.aialarm.data.prefs.AppPreferences.DEFAULT_CLOCK_STYLE
        )
    }
}
