package com.riderai.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riderai.RideState
import com.riderai.RideViewModel
import com.riderai.safety.AlertLevel
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Minimal, high-contrast heads-up display. With mirror on, the whole layout is flipped so it
 * reads correctly when reflected in a windscreen or a HUD combiner glass.
 * Tap the screen to show / hide the controls.
 */
@Composable
fun HudScreen(s: RideState, speedLimit: Int, vm: RideViewModel) {
    var showControls by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .clickable { showControls = !showControls }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .graphicsLayer { if (s.mirrorHud) scaleX = -1f },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CollisionStrip(s)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(compass(s.headingDeg), color = HudGreen, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                RecBadge(s)
            }
            Spacer(Modifier.weight(1f))
            SpeedReadout(s, compact = false, limit = speedLimit)
            Spacer(Modifier.height(12.dp))
            CollisionLine(s)
            Spacer(Modifier.weight(1f))
            LeanGauge(s.leanDeg)
            Text(
                "Lean ${abs(s.leanDeg).roundToInt()}°  ·  max L ${s.maxLeanLeft.roundToInt()}° R ${s.maxLeanRight.roundToInt()}°" +
                    "  ·  ${"%.1f".format(s.tripKm)} km",
                color = HudDim, fontSize = 18.sp,
            )
            Spacer(Modifier.height(16.dp))
        }
        Banner(s, Modifier.align(Alignment.Center).graphicsLayer { if (s.mirrorHud) scaleX = -1f })
        if (showControls) ControlBar(s, vm, Modifier.align(Alignment.BottomCenter))
    }
}

/** Full-width colour strip: green clear, amber warning, red brake. */
@Composable
private fun CollisionStrip(s: RideState) {
    val c = when (s.collision.level) {
        AlertLevel.NONE -> HudGreen.copy(alpha = 0.35f)
        else -> levelColor(s.collision.level)
    }
    Box(Modifier.fillMaxWidth().height(if (s.collision.level == AlertLevel.CRITICAL) 36.dp else 10.dp).background(c))
}

@Composable
private fun LeanGauge(leanDeg: Double) {
    Canvas(Modifier.fillMaxWidth().height(90.dp).padding(horizontal = 48.dp)) {
        val cx = size.width / 2
        val cy = size.height
        val r = size.height * 0.95f
        // Arc from -60° to +60° around "up".
        drawArc(
            color = HudDim, startAngle = 210f, sweepAngle = 120f, useCenter = false,
            topLeft = Offset(cx - r, cy - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            style = Stroke(width = 6f),
        )
        val a = Math.toRadians(-90.0 + leanDeg.coerceIn(-60.0, 60.0))
        val end = Offset(cx + (r * Math.cos(a)).toFloat(), cy + (r * Math.sin(a)).toFloat())
        drawLine(
            color = if (abs(leanDeg) > 45) HudAmber else HudGreen,
            start = Offset(cx, cy), end = end, strokeWidth = 10f, cap = StrokeCap.Round,
        )
    }
}

private fun compass(deg: Float?): String {
    if (deg == null) return "—"
    val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return dirs[((deg % 360 + 360) % 360 / 45f).roundToInt() % 8] + " ${deg.roundToInt()}°"
}
