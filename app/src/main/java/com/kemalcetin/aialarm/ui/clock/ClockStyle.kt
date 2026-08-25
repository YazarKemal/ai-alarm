package com.kemalcetin.aialarm.ui.clock

import androidx.annotation.StringRes
import com.kemalcetin.aialarm.R

/**
 * Selectable clock faces. Presentation only — never affects alarm scheduling.
 */
enum class ClockStyle(@StringRes val labelRes: Int) {
    DIGITAL_MINIMAL(R.string.clock_style_digital_minimal),
    DIGITAL_BOLD(R.string.clock_style_digital_bold),
    DIGITAL_NIGHT(R.string.clock_style_digital_night),
    ANALOG_CLASSIC(R.string.clock_style_analog_classic),
    ANALOG_MINIMAL(R.string.clock_style_analog_minimal),
    ANALOG_GOLD(R.string.clock_style_analog_gold);

    val isDigital: Boolean
        get() = name.startsWith("DIGITAL")
}
