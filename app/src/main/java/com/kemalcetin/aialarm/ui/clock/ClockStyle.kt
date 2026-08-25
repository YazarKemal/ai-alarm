package com.kemalcetin.aialarm.ui.clock

/**
 * Selectable clock faces. Presentation only — never affects alarm scheduling.
 */
enum class ClockStyle(val label: String) {
    DIGITAL_MINIMAL("Digital Minimal"),
    DIGITAL_BOLD("Digital Bold"),
    DIGITAL_NIGHT("Digital Night"),
    ANALOG_CLASSIC("Analog Classic"),
    ANALOG_MINIMAL("Analog Minimal"),
    ANALOG_GOLD("Analog Gold");

    val isDigital: Boolean
        get() = name.startsWith("DIGITAL")
}
