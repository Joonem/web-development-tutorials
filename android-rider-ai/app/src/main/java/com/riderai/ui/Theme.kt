package com.riderai.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val HudGreen = Color(0xFF00E5A0)
val HudAmber = Color(0xFFFFB300)
val HudRed = Color(0xFFFF3B30)
val HudDim = Color(0xFF7A8A94)

@Composable
fun RiderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = HudGreen,
            secondary = HudAmber,
            error = HudRed,
            background = Color.Black,
            surface = Color(0xFF101418),
        ),
        content = content,
    )
}
