package com.riderai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riderai.RideViewModel
import com.riderai.ScreenMode
import com.riderai.data.RiderPrefs

@Composable
fun SettingsScreen(initial: RiderPrefs, vm: RideViewModel) {
    var p by remember(initial) { mutableStateOf(initial) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E11))
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Settings", color = HudGreen, fontSize = 28.sp, fontWeight = FontWeight.Bold)

        Section("AI assistant")
        OutlinedTextField(
            value = p.apiKey, onValueChange = { p = p.copy(apiKey = it) },
            label = { Text("Claude API key") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = p.wakeWord, onValueChange = { p = p.copy(wakeWord = it) },
            label = { Text("Wake word") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Toggle("Hands-free listening (wake word)", p.handsFree) { p = p.copy(handsFree = it) }

        Section("Safety assist")
        Toggle("Forward collision & headway warning", p.collisionWarning) { p = p.copy(collisionWarning = it) }
        NumberField("Speed limit alert (km/h, 0 = off)", p.speedLimitKmh) { p = p.copy(speedLimitKmh = it) }
        NumberField("Lean angle alert (°)", p.maxLeanDeg) { p = p.copy(maxLeanDeg = it) }
        NumberField("Camera vertical field of view (°)", p.cameraVFovDeg.toInt()) { p = p.copy(cameraVFovDeg = it.toFloat()) }

        Section("Crash detection & SOS")
        Toggle("Crash detection", p.crashDetection) { p = p.copy(crashDetection = it) }
        OutlinedTextField(
            value = p.riderName, onValueChange = { p = p.copy(riderName = it) },
            label = { Text("Your name (in the SOS text)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = p.emergencyContact, onValueChange = { p = p.copy(emergencyContact = it) },
            label = { Text("Emergency contact phone") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        NumberField("SOS countdown (s)", p.sosCountdownSec) { p = p.copy(sosCountdownSec = it) }

        Section("Dashcam")
        Toggle("Start recording automatically", p.autoRecord) { p = p.copy(autoRecord = it) }
        NumberField("Segment length (min)", p.segmentMinutes) { p = p.copy(segmentMinutes = it) }
        NumberField("Loop storage (MB)", p.loopStorageMb) { p = p.copy(loopStorageMb = it) }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::calibrateLean) { Text("Zero lean (bike upright)") }
            OutlinedButton(onClick = vm::resetTrip) { Text("Reset trip") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.triggerSos("SOS test") }) { Text("Test SOS countdown") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.updatePrefs(p); vm.setMode(ScreenMode.CAMERA) }) { Text("Save") }
            OutlinedButton(onClick = { vm.setMode(ScreenMode.CAMERA) }) { Text("Cancel") }
        }
        Text(
            "The API key is stored only on this phone and sent only to the Claude API. " +
                "Rider AI is a riding aid, not a safety system: always ride as if it were off.",
            color = HudDim, fontSize = 13.sp,
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title.uppercase(), color = HudDim, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun NumberField(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t.filter(Char::isDigit).take(5)
            text.toIntOrNull()?.let(onChange)
        },
        label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
