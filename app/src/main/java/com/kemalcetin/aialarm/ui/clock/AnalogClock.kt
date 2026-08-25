package com.kemalcetin.aialarm.ui.clock

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import java.time.LocalTime

// Canonical PromptHaven gold accents (matched to Color.kt).
private val Gold = Color(0xFFFFD700)
private val Amber = Color(0xFFFFBF00)
private val BrandBlack = Color(0xFF050505)

internal enum class AnalogVariant {
    CLASSIC, MINIMAL, GOLD
}

internal fun ClockStyle.toAnalogVariant(): AnalogVariant = when (this) {
    ClockStyle.ANALOG_CLASSIC -> AnalogVariant.CLASSIC
    ClockStyle.ANALOG_MINIMAL -> AnalogVariant.MINIMAL
    ClockStyle.ANALOG_GOLD -> AnalogVariant.GOLD
    else -> AnalogVariant.CLASSIC
}

/**
 * A real analog clock drawn with [Canvas]. The draw block only recomposes when
 * [time] changes (the ticking [ClockWidget] passes a fresh value each second),
 * so frames are cheap and whole-screen recomposition is avoided.
 */
@Composable
fun AnalogClock(
    time: LocalTime,
    style: ClockStyle,
    modifier: Modifier = Modifier
) {
    val variant = style.toAnalogVariant()
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val c = center

        val face = when (variant) {
            AnalogVariant.GOLD -> BrandBlack
            AnalogVariant.CLASSIC -> scheme.surface
            AnalogVariant.MINIMAL -> Color.Transparent
        }
        if (face != Color.Transparent) {
            drawCircle(face, radius = radius)
        }
        if (variant == AnalogVariant.CLASSIC) {
            drawCircle(
                scheme.outline.copy(alpha = 0.5f),
                radius = radius,
                style = Stroke(1.dp.toPx())
            )
        }

        drawMarkers(variant, radius, c, scheme)

        val secF = time.second / 60f
        val minF = (time.minute + secF) / 60f
        val hourF = (time.hour % 12 + minF) / 12f

        // Hour hand
        rotate(hourF * 360f, c) {
            drawLine(
                color = hourHandColor(variant, scheme),
                start = c,
                end = Offset(c.x, c.y - radius * 0.5f),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        // Minute hand
        rotate(minF * 360f, c) {
            drawLine(
                color = minuteHandColor(variant, scheme),
                start = c,
                end = Offset(c.x, c.y - radius * 0.72f),
                strokeWidth = 3.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        // Second hand (subtle)
        rotate(secF * 360f, c) {
            drawLine(
                color = secondHandColor(variant, scheme),
                start = Offset(c.x, c.y + radius * 0.14f),
                end = Offset(c.x, c.y - radius * 0.82f),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        drawCircle(Gold, radius = 4.dp.toPx(), center = c)
    }
}

private fun DrawScope.drawMarkers(
    variant: AnalogVariant,
    radius: Float,
    c: Offset,
    scheme: androidx.compose.material3.ColorScheme
) {
    val isGold = variant == AnalogVariant.GOLD
    val markerColor = when {
        isGold -> Gold.copy(alpha = 0.55f)
        variant == AnalogVariant.MINIMAL -> scheme.outline.copy(alpha = 0.7f)
        else -> scheme.outline
    }
    val majorWidth = 3.dp.toPx()
    val minorWidth = 1.5.dp.toPx()

    when (variant) {
        AnalogVariant.MINIMAL -> {
            // Four cardinal markers only.
            listOf(0, 90, 180, 270).forEach { angle ->
                rotate(angle.toFloat(), c) {
                    drawLine(
                        color = markerColor,
                        start = Offset(c.x, c.y - radius * 0.9f),
                        end = Offset(c.x, c.y - radius * 0.78f),
                        strokeWidth = majorWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
        else -> {
            val count = if (variant == AnalogVariant.CLASSIC) 12 else 12
            repeat(count) { i ->
                val angle = i * 30f
                val inner = radius * if (variant == AnalogVariant.GOLD) 0.86f else 0.90f
                rotate(angle, c) {
                    drawLine(
                        color = markerColor,
                        start = Offset(c.x, c.y - radius * inner),
                        end = Offset(c.x, c.y - radius * 0.97f),
                        strokeWidth = majorWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
            if (variant == AnalogVariant.GOLD) {
                // Subtle minute markers between the hour markers.
                repeat(60) { i ->
                    if (i % 5 != 0) {
                        rotate(i * 6f, c) {
                            drawLine(
                                color = markerColor.copy(alpha = 0.3f),
                                start = Offset(c.x, c.y - radius * 0.93f),
                                end = Offset(c.x, c.y - radius * 0.97f),
                                strokeWidth = minorWidth,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun hourHandColor(variant: AnalogVariant, scheme: androidx.compose.material3.ColorScheme): Color =
    when (variant) {
        AnalogVariant.GOLD -> Gold
        AnalogVariant.MINIMAL -> scheme.onSurface
        AnalogVariant.CLASSIC -> scheme.onSurface
    }

private fun minuteHandColor(variant: AnalogVariant, scheme: androidx.compose.material3.ColorScheme): Color =
    when (variant) {
        AnalogVariant.GOLD -> Amber
        AnalogVariant.MINIMAL -> scheme.primary
        AnalogVariant.CLASSIC -> scheme.primary
    }

private fun secondHandColor(variant: AnalogVariant, scheme: androidx.compose.material3.ColorScheme): Color =
    when (variant) {
        AnalogVariant.GOLD -> Gold.copy(alpha = 0.6f)
        AnalogVariant.MINIMAL -> scheme.tertiary.copy(alpha = 0.7f)
        AnalogVariant.CLASSIC -> scheme.tertiary.copy(alpha = 0.7f)
    }
