package com.riderai.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riderai.RideState
import com.riderai.RideViewModel
import com.riderai.ScreenMode
import com.riderai.safety.AlertLevel
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun RiderApp(vm: RideViewModel, cameraGranted: Boolean, onRequestPermissions: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()

    if (!cameraGranted) {
        PermissionScreen(onRequestPermissions)
        return
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // The camera stays bound in every mode so the dashcam keeps recording; in HUD and
        // settings it shrinks to a small view underneath those screens.
        val camModifier = if (state.mode == ScreenMode.CAMERA) Modifier.fillMaxSize()
        else Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(12.dp).size(width = 128.dp, height = 96.dp)
            .border(1.dp, HudDim, RoundedCornerShape(6.dp))
        CameraPreview(vm, camModifier)

        when (state.mode) {
            ScreenMode.CAMERA -> CameraOverlay(state, vm)
            ScreenMode.HUD -> HudScreen(state, prefs.speedLimitKmh, vm)
            ScreenMode.SETTINGS -> SettingsScreen(prefs, vm)
        }

        state.sosSecondsLeft?.let { SosOverlay(it, state.sosReason ?: "Emergency", vm::cancelSos) }
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color.Black).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Rider AI", color = HudGreen, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text(
            "Camera is needed for the dashcam and collision warning. Microphone enables voice " +
                "commands, location gives speed and SOS position, SMS sends the crash alert.",
            color = Color.White, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) { Text("Grant permissions") }
    }
}

@Composable
private fun BoxScope.CameraOverlay(s: RideState, vm: RideViewModel) {
    // Detection boxes; the in-path target is highlighted by alert colour.
    val alertColor = levelColor(s.collision.level)
    Canvas(Modifier.fillMaxSize()) {
        // Path band the collision logic watches.
        drawRect(Color.White.copy(alpha = 0.08f), Offset(size.width * 0.30f, 0f), Size(size.width * 0.40f, size.height))
        for (b in s.boxes) {
            val inPath = b.centerX in 0.30f..0.70f
            drawRect(
                color = if (inPath) alertColor else Color.White.copy(alpha = 0.5f),
                topLeft = Offset(b.left * size.width, b.top * size.height),
                size = Size(b.width * size.width, b.height * size.height),
                style = Stroke(width = if (inPath) 6f else 2f),
            )
        }
    }

    Column(Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(16.dp)) {
        SpeedReadout(s, compact = true)
        CollisionLine(s)
        Text("Lean ${s.leanDeg.roundToInt()}°  ·  ${"%.1f".format(Locale.US, s.gForce)} g", color = Color.White, fontSize = 16.sp)
    }
    RecBadge(s, Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(16.dp))
    Banner(s, Modifier.align(Alignment.Center))
    ControlBar(s, vm, Modifier.align(Alignment.BottomCenter))
}

@Composable
fun SpeedReadout(s: RideState, compact: Boolean, limit: Int = 0) {
    val color = if (s.overspeed) HudRed else Color.White
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            s.speedKmh.roundToInt().toString(),
            color = color,
            fontSize = if (compact) 56.sp else 160.sp,
            fontWeight = FontWeight.Black,
            lineHeight = if (compact) 56.sp else 150.sp,
        )
        Text(" km/h", color = HudDim, fontSize = if (compact) 18.sp else 28.sp, modifier = Modifier.padding(bottom = 8.dp))
        if (!s.gpsFix) Text("  no GPS", color = HudAmber, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
    }
    if (limit > 0 && !compact) Text("Limit $limit", color = if (s.overspeed) HudRed else HudDim, fontSize = 22.sp)
}

@Composable
fun CollisionLine(s: RideState) {
    val a = s.collision
    val text = when (a.level) {
        AlertLevel.CRITICAL -> "BRAKE  TTC ${"%.1f".format(Locale.US, a.ttcSeconds ?: 0.0)} s"
        AlertLevel.WARNING -> "Vehicle ahead  TTC ${"%.1f".format(Locale.US, a.ttcSeconds ?: 0.0)} s"
        AlertLevel.CAUTION -> "Close following  ${"%.1f".format(Locale.US, a.headwaySeconds ?: 0.0)} s gap"
        AlertLevel.NONE -> a.headwaySeconds?.let { "Gap ${"%.1f".format(Locale.US, it)} s" } ?: "Road clear"
    }
    Text(text, color = levelColor(a.level), fontSize = 20.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun RecBadge(s: RideState, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (s.recording) {
            Box(Modifier.size(14.dp).background(HudRed, CircleShape))
            Text(" REC", color = HudRed, fontWeight = FontWeight.Bold)
        } else {
            Text("REC off", color = HudDim)
        }
        if (s.savedClips > 0) Text("  ·  ${s.savedClips} saved", color = HudDim)
        if (s.listening) Text("  ·  listening", color = HudGreen)
        if (s.muted) Text("  ·  muted", color = HudAmber)
    }
}

@Composable
fun Banner(s: RideState, modifier: Modifier = Modifier) {
    val msg = s.banner ?: return
    val bg = when (s.bannerLevel) {
        AlertLevel.CRITICAL -> HudRed
        AlertLevel.WARNING -> HudAmber
        AlertLevel.CAUTION -> HudAmber.copy(alpha = 0.85f)
        AlertLevel.NONE -> Color(0xCC101418)
    }
    Text(
        msg,
        modifier = modifier.padding(24.dp).background(bg, RoundedCornerShape(12.dp)).padding(horizontal = 24.dp, vertical = 14.dp),
        color = if (s.bannerLevel == AlertLevel.NONE) Color.White else Color.Black,
        fontSize = if (s.bannerLevel == AlertLevel.CRITICAL) 44.sp else 26.sp,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center,
    )
}

@Composable
fun ControlBar(s: RideState, vm: RideViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().background(Color(0x99000000)).safeDrawingPadding().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val heard = s.assistantReply ?: s.lastHeard
        if (s.assistantBusy) Text("Thinking…", color = HudGreen)
        else if (heard != null) Text(heard, color = Color.White, maxLines = 2, fontSize = 14.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = vm::toggleRecording) { Text(if (s.recording) "Stop" else "Record") }
            FilledTonalButton(onClick = vm::saveClip) { Text("Save clip") }
            if (s.voiceAvailable) {
                Button(onClick = vm::pushToTalk) { Text(if (s.listening) "Listening" else "Talk") }
            }
            FilledTonalButton(onClick = {
                vm.setMode(if (s.mode == ScreenMode.HUD) ScreenMode.CAMERA else ScreenMode.HUD)
            }) { Text(if (s.mode == ScreenMode.HUD) "Camera" else "HUD") }
            FilledTonalButton(onClick = { vm.setMode(ScreenMode.SETTINGS) }) { Text("⚙") }
        }
    }
}

@Composable
private fun SosOverlay(secondsLeft: Int, reason: String, onCancel: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(HudRed.copy(alpha = 0.92f)).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(reason.uppercase(), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text("Sending SOS in", color = Color.White, fontSize = 22.sp)
        Text("$secondsLeft", color = Color.White, fontSize = 140.sp, fontWeight = FontWeight.Black)
        Button(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = HudRed),
            modifier = Modifier.fillMaxWidth().height(96.dp),
        ) { Text("I'M OK — CANCEL", fontSize = 28.sp, fontWeight = FontWeight.Black) }
        Spacer(Modifier.height(12.dp))
        Text("Or say \"I'm OK\"", color = Color.White, fontSize = 18.sp)
    }
}

fun levelColor(level: AlertLevel): Color = when (level) {
    AlertLevel.CRITICAL -> HudRed
    AlertLevel.WARNING -> HudAmber
    AlertLevel.CAUTION -> HudAmber
    AlertLevel.NONE -> HudGreen
}
