package com.riderai

import android.app.Application
import android.location.Location
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.riderai.ai.RideContext
import com.riderai.ai.RiderAssistant
import com.riderai.dashcam.DashcamRecorder
import com.riderai.data.RiderPrefs
import com.riderai.data.RiderSettings
import com.riderai.safety.AlertLevel
import com.riderai.safety.CollisionAssessment
import com.riderai.safety.CollisionRiskEstimator
import com.riderai.safety.CrashDetector
import com.riderai.safety.RoadObjectAnalyzer
import com.riderai.safety.SosManager
import com.riderai.safety.TrackedBox
import com.riderai.sensors.LocationTracker
import com.riderai.sensors.MotionSample
import com.riderai.sensors.MotionTracker
import com.riderai.voice.CommandParser
import com.riderai.voice.VoiceCommand
import com.riderai.voice.VoiceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ScreenMode { CAMERA, HUD, SETTINGS }

data class RideState(
    val mode: ScreenMode = ScreenMode.CAMERA,
    val mirrorHud: Boolean = false,
    val speedKmh: Double = 0.0,
    val headingDeg: Float? = null,
    val gpsFix: Boolean = false,
    val lat: Double? = null,
    val lon: Double? = null,
    val tripKm: Double = 0.0,
    val rideStartMs: Long = System.currentTimeMillis(),
    val leanDeg: Double = 0.0,
    val maxLeanLeft: Double = 0.0,
    val maxLeanRight: Double = 0.0,
    val gForce: Double = 1.0,
    val recording: Boolean = false,
    val savedClips: Int = 0,
    val collision: CollisionAssessment = CollisionAssessment(AlertLevel.NONE, null, null, null, null),
    val boxes: List<TrackedBox> = emptyList(),
    val banner: String? = null,
    val bannerLevel: AlertLevel = AlertLevel.NONE,
    val overspeed: Boolean = false,
    val listening: Boolean = false,
    val lastHeard: String? = null,
    val assistantReply: String? = null,
    val assistantBusy: Boolean = false,
    val muted: Boolean = false,
    val sosSecondsLeft: Int? = null,
    val sosReason: String? = null,
    val voiceAvailable: Boolean = true,
)

class RideViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = RiderSettings(app)
    private val _prefs = MutableStateFlow(settings.load())
    val prefs: StateFlow<RiderPrefs> = _prefs.asStateFlow()

    private val _state = MutableStateFlow(RideState())
    val state: StateFlow<RideState> = _state.asStateFlow()

    // --- Safety --------------------------------------------------------------------------
    private var collisionEstimator = CollisionRiskEstimator(verticalFovDegrees = _prefs.value.cameraVFovDeg.toDouble())
    private val crashDetector = CrashDetector()
    private val sos = SosManager(app)
    private var sosJob: Job? = null
    private val lastSpoken = HashMap<String, Long>()

    // --- Camera use cases (bound to the activity lifecycle by the UI) ---------------------
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val analyzer = RoadObjectAnalyzer { boxes, ts -> onBoxes(boxes, ts) }
        .also { it.enabled = _prefs.value.collisionWarning }
    val imageAnalysis: ImageAnalysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                )
                .build()
        )
        .build()
        .also { it.setAnalyzer(analysisExecutor, analyzer) }

    val dashcam = DashcamRecorder(
        app,
        onStateChanged = { rec -> _state.update { it.copy(recording = rec) } },
        onClipSaved = { name ->
            _state.update { it.copy(savedClips = it.savedClips + 1) }
            flash("Clip saved to gallery", AlertLevel.NONE)
        },
    ).also { applyDashcamPrefs(it, _prefs.value) }

    // --- Sensors & voice -----------------------------------------------------------------
    private val location = LocationTracker(app, ::onLocation)
    private val motion = MotionTracker(app, ::onMotion).also { it.leanOffsetDeg = _prefs.value.leanOffsetDeg.toDouble() }
    private var lastLocation: Location? = null
    private var lastMotionUiMs = 0L

    private var parser = CommandParser(_prefs.value.wakeWord)
    private var voice: VoiceEngine? = null
    private var assistant: RiderAssistant? = null
    private var assistantKey: String? = null

    private var started = false

    /** Called once permissions are known. */
    fun start(hasLocation: Boolean, hasMic: Boolean) {
        if (started) return
        started = true
        if (hasLocation) location.start()
        motion.start()
        if (hasMic) {
            voice = VoiceEngine(
                getApplication(),
                onTranscript = ::onTranscript,
                onListeningChanged = { l -> _state.update { it.copy(listening = l) } },
            ).also {
                _state.update { s -> s.copy(voiceAvailable = it.available) }
                it.handsFree = _prefs.value.handsFree
            }
        } else {
            _state.update { it.copy(voiceAvailable = false) }
        }
    }

    private var autoStarted = false

    fun onCameraBound() {
        dashcam.resumeIfInterrupted()
        if (_prefs.value.autoRecord && !autoStarted && !dashcam.isRecording) {
            autoStarted = true
            dashcam.start()
        }
    }

    // --- UI actions ----------------------------------------------------------------------
    fun setMode(mode: ScreenMode) = _state.update { it.copy(mode = mode) }
    fun toggleMirror() = _state.update { it.copy(mirrorHud = !it.mirrorHud) }
    fun toggleRecording() = if (dashcam.isRecording) dashcam.stop() else dashcam.start()
    fun saveClip() = dashcam.saveClip().also { flash("Saving clip…", AlertLevel.NONE) }
    fun pushToTalk() = voice?.listenOnce()
    fun toggleMute() = _state.update { it.copy(muted = !it.muted) }

    fun calibrateLean() {
        val offset = motion.currentRawLean
        motion.leanOffsetDeg = offset
        updatePrefs(_prefs.value.copy(leanOffsetDeg = offset.toFloat()))
        _state.update { it.copy(maxLeanLeft = 0.0, maxLeanRight = 0.0) }
        flash("Lean angle zeroed", AlertLevel.NONE)
    }

    fun resetTrip() {
        _state.update {
            it.copy(tripKm = 0.0, rideStartMs = System.currentTimeMillis(), maxLeanLeft = 0.0, maxLeanRight = 0.0)
        }
    }

    fun updatePrefs(p: RiderPrefs) {
        settings.save(p)
        val saved = settings.load()
        _prefs.value = saved
        parser = CommandParser(saved.wakeWord)
        voice?.handsFree = saved.handsFree
        applyDashcamPrefs(dashcam, saved)
        collisionEstimator = CollisionRiskEstimator(verticalFovDegrees = saved.cameraVFovDeg.toDouble())
        analyzer.enabled = saved.collisionWarning
    }

    fun triggerSos(reason: String) {
        if (sosJob != null) return
        val total = _prefs.value.sosCountdownSec
        dashcam.saveClip()
        say("$reason. Say: I'm OK, to cancel. Sending S O S in $total seconds.", critical = true)
        sosJob = viewModelScope.launch {
            for (left in total downTo 1) {
                _state.update { it.copy(sosSecondsLeft = left, sosReason = reason) }
                if (left != total && left % 10 == 0) say("S O S in $left seconds.", critical = true)
                delay(1000)
            }
            val s = _state.value
            val result = sos.dispatch(_prefs.value.emergencyContact, _prefs.value.riderName, s.lat, s.lon, reason)
            _state.update { it.copy(sosSecondsLeft = null) }
            say(result, critical = true)
            sosJob = null
        }
    }

    fun cancelSos() {
        sosJob?.cancel()
        sosJob = null
        crashDetector.reset()
        _state.update { it.copy(sosSecondsLeft = null, sosReason = null) }
        say("Emergency cancelled. Ride safe.", critical = true)
    }

    // --- Sensor callbacks ----------------------------------------------------------------
    private fun onLocation(loc: Location) {
        if (loc.provider == "network" && lastLocation?.provider == "gps" &&
            loc.time - (lastLocation?.time ?: 0) < 5000) return
        val prev = lastLocation
        val speedKmh = if (loc.hasSpeed()) loc.speed * 3.6 else 0.0
        val addKm = if (prev != null && loc.accuracy < 30f && speedKmh > 3) prev.distanceTo(loc) / 1000.0 else 0.0
        lastLocation = loc
        val limit = _prefs.value.speedLimitKmh
        val over = limit > 0 && speedKmh > limit + 2
        _state.update {
            it.copy(
                speedKmh = speedKmh,
                headingDeg = if (loc.hasBearing()) loc.bearing else it.headingDeg,
                gpsFix = loc.provider == "gps",
                lat = loc.latitude,
                lon = loc.longitude,
                tripKm = it.tripKm + addKm,
                overspeed = over,
            )
        }
        if (over) alert("speed", 30_000, "Speed limit $limit", AlertLevel.CAUTION)
    }

    private fun onMotion(m: MotionSample) {
        if (_prefs.value.crashDetection && sosJob == null &&
            crashDetector.onSample(m.ax, m.ay, m.az, m.leanDeg, m.timestampMs)
        ) {
            triggerSos("Crash detected")
        }
        val s = _state.value
        dashcam.addTelemetry(s.speedKmh, s.lat, s.lon, m.leanDeg, m.gForce)
        if (m.timestampMs - lastMotionUiMs < 200) return
        lastMotionUiMs = m.timestampMs

        val moving = s.speedKmh > 10
        _state.update {
            it.copy(
                leanDeg = m.leanDeg,
                gForce = m.gForce,
                maxLeanLeft = if (moving && m.leanDeg < 0) maxOf(it.maxLeanLeft, -m.leanDeg) else it.maxLeanLeft,
                maxLeanRight = if (moving && m.leanDeg > 0) maxOf(it.maxLeanRight, m.leanDeg) else it.maxLeanRight,
            )
        }
        val maxLean = _prefs.value.maxLeanDeg
        if (moving && abs(m.leanDeg) > maxLean) {
            alert("lean", 10_000, "Lean ${abs(m.leanDeg).roundToInt()} degrees", AlertLevel.CAUTION)
        }
    }

    private fun onBoxes(boxes: List<TrackedBox>, ts: Long) {
        if (!_prefs.value.collisionWarning) return
        val a = collisionEstimator.update(boxes, ts, _state.value.speedKmh)
        _state.update { it.copy(collision = a, boxes = boxes) }
        when (a.level) {
            AlertLevel.CRITICAL -> alert("fcw-critical", 2_500, "Brake! Brake!", AlertLevel.CRITICAL)
            AlertLevel.WARNING -> alert("fcw", 4_000, "Vehicle ahead", AlertLevel.WARNING)
            AlertLevel.CAUTION -> alert("headway", 15_000, "Keep your distance", AlertLevel.CAUTION)
            AlertLevel.NONE -> Unit
        }
    }

    // --- Voice ---------------------------------------------------------------------------
    private fun onTranscript(text: String, fromHandsFree: Boolean) {
        val sosActive = sosJob != null
        val cmd = parser.parse(text, requireWakeWord = fromHandsFree && !sosActive) ?: return
        _state.update { it.copy(lastHeard = text) }
        if (sosActive && cmd !is VoiceCommand.CancelSos) return
        execute(cmd)
    }

    private fun execute(cmd: VoiceCommand) {
        val s = _state.value
        when (cmd) {
            VoiceCommand.StartRecording -> { dashcam.start(); say("Dashcam recording.") }
            VoiceCommand.StopRecording -> { dashcam.stop(); say("Dashcam stopped.") }
            VoiceCommand.SaveClip -> { saveClip(); say("Clip saved.") }
            VoiceCommand.ShowHud -> { setMode(ScreenMode.HUD); say("HUD.") }
            VoiceCommand.ShowCamera -> { setMode(ScreenMode.CAMERA); say("Camera.") }
            VoiceCommand.ToggleMirror -> { toggleMirror(); setMode(ScreenMode.HUD); say("Mirror ${if (!s.mirrorHud) "on" else "off"}.") }
            VoiceCommand.WhatSpeed -> say("${s.speedKmh.roundToInt()} kilometres per hour.")
            VoiceCommand.Status -> say(statusLine())
            VoiceCommand.MuteAlerts -> { _state.update { it.copy(muted = true) }; say("Alerts muted. Critical warnings stay on.", critical = true) }
            VoiceCommand.UnmuteAlerts -> { _state.update { it.copy(muted = false) }; say("Alerts on.") }
            VoiceCommand.Sos -> triggerSos("Rider requested help")
            VoiceCommand.CancelSos -> if (sosJob != null) cancelSos()
            VoiceCommand.Help -> say(
                "Say ${_prefs.value.wakeWord}, then: save clip, start or stop recording, HUD, mirror, speed, status, mute, S O S, or ask me anything."
            )
            is VoiceCommand.AskAssistant -> ask(cmd.question)
        }
    }

    fun ask(question: String) {
        val key = _prefs.value.apiKey
        if (key.isBlank()) {
            say("Add your Claude API key in settings so I can answer questions.")
            return
        }
        if (assistant == null || assistantKey != key) {
            assistant = RiderAssistant(key)
            assistantKey = key
        }
        val ai = assistant ?: return
        val ctx = rideContext()
        _state.update { it.copy(assistantBusy = true) }
        viewModelScope.launch {
            val reply = withContext(Dispatchers.IO) {
                runCatching { ai.ask(question, ctx) }.getOrElse { "Sorry, something went wrong." }
            }
            _state.update { it.copy(assistantBusy = false, assistantReply = reply) }
            say(reply)
        }
    }

    private fun rideContext(): RideContext {
        val s = _state.value
        return RideContext(
            speedKmh = s.speedKmh,
            speedLimitKmh = _prefs.value.speedLimitKmh,
            lat = s.lat,
            lon = s.lon,
            headingDeg = s.headingDeg,
            leanDeg = s.leanDeg,
            recording = s.recording,
            tripKm = s.tripKm,
            rideMinutes = (System.currentTimeMillis() - s.rideStartMs) / 60_000,
            localTime = SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date()),
        )
    }

    private fun statusLine(): String {
        val s = _state.value
        val mins = (System.currentTimeMillis() - s.rideStartMs) / 60_000
        return "Trip ${"%.1f".format(s.tripKm)} kilometres in $mins minutes. " +
            "Dashcam ${if (s.recording) "recording" else "off"}. " +
            "GPS ${if (s.gpsFix) "locked" else "searching"}. " +
            "Max lean ${s.maxLeanLeft.roundToInt()} left, ${s.maxLeanRight.roundToInt()} right."
    }

    // --- Alerts --------------------------------------------------------------------------
    private fun alert(key: String, cooldownMs: Long, message: String, level: AlertLevel) {
        val now = System.currentTimeMillis()
        if (now - (lastSpoken[key] ?: 0) < cooldownMs) return
        lastSpoken[key] = now
        flash(message, level)
        val critical = level == AlertLevel.CRITICAL
        voice?.beep(critical)
        say(message, critical = critical)
    }

    private fun say(text: String, critical: Boolean = false) {
        if (_state.value.muted && !critical) return
        voice?.speak(text, urgent = critical)
    }

    private var bannerJob: Job? = null
    private fun flash(message: String, level: AlertLevel) {
        _state.update { it.copy(banner = message, bannerLevel = level) }
        bannerJob?.cancel()
        bannerJob = viewModelScope.launch {
            delay(if (level == AlertLevel.CRITICAL) 2500 else 3500)
            _state.update { it.copy(banner = null, bannerLevel = AlertLevel.NONE) }
        }
    }

    private fun applyDashcamPrefs(d: DashcamRecorder, p: RiderPrefs) {
        d.segmentMs = p.segmentMinutes * 60_000L
        d.loopBudgetBytes = p.loopStorageMb.toLong() * 1024 * 1024
    }

    override fun onCleared() {
        location.stop()
        motion.stop()
        voice?.release()
        dashcam.release()
        analyzer.close()
        analysisExecutor.shutdown()
    }
}
