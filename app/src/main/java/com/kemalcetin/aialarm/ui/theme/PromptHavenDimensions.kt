package com.kemalcetin.aialarm.ui.theme

import androidx.compose.ui.unit.dp

/**
 * PromptHaven design tokens for geometry. Keeps radii, spacing and sizes
 * centralized so the UI stays consistent and Framer/Figma values can be swapped
 * in one place.
 */
object PhSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val section = 28.dp
}

object PhRadius {
    val card = 20.dp
    val cardHero = 28.dp
    val tile = 14.dp
    val chip = 12.dp
    val button = 16.dp
}

object PhSize {
    val touchTarget = 48.dp
    val ringingAction = 64.dp
    val iconTile = 44.dp
    val clockFace = 240.dp
}
