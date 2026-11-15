package com.riderai.dashcam

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Loop-recording dashcam on top of CameraX [VideoCapture].
 *
 *  - Records fixed-length segments into app storage and deletes the oldest when the loop
 *    exceeds its storage budget.
 *  - Writes a .srt telemetry track (speed, GPS, lean, g) next to every segment.
 *  - "Save clip" (voice, button, or automatic on crash) protects the current and previous
 *    segment by copying them, with telemetry, to the phone's gallery (Movies/RiderAI).
 *
 * Audio is off: the microphone is kept free for voice commands.
 * All methods must be called on the main thread.
 */
class DashcamRecorder(
    private val context: Context,
    private val onStateChanged: (recording: Boolean) -> Unit,
    private val onClipSaved: (name: String) -> Unit,
) {
    val recorder: Recorder = Recorder.Builder()
        .setQualitySelector(
            QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
        )
        .build()
    val videoCapture: VideoCapture<Recorder> = VideoCapture.withOutput(recorder)

    var segmentMs: Long = 3 * 60_000L
    var loopBudgetBytes: Long = 4L * 1024 * 1024 * 1024

    private val main = Handler(Looper.getMainLooper())
    private val loopDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "dashcam/loop").apply { mkdirs() }
    private var recording: Recording? = null
    private var segmentFile: File? = null
    private var segmentStartMs = 0L
    private var previousSegment: File? = null
    private var keepGoing = false
    private var interrupted = false
    private var lockCurrent = false
    private val telemetry = ArrayList<TelemetryPoint>()
    private val io = Executors.newSingleThreadExecutor()
    private val exporting = ConcurrentHashMap.newKeySet<File>()

    val isRecording: Boolean get() = recording != null

    private val rotate = Runnable { recording?.stop() } // Finalize starts the next segment

    fun start() {
        if (recording != null) return
        keepGoing = true
        startSegment()
    }

    /** Restarts recording that was cut off because the camera closed. */
    fun resumeIfInterrupted() {
        if (interrupted) {
            interrupted = false
            start()
        }
    }

    fun stop() {
        interrupted = false
        keepGoing = false
        main.removeCallbacks(rotate)
        recording?.stop()
    }

    /** Protects the footage around "now". */
    fun saveClip() {
        if (recording == null) {
            previousSegment?.let { export(it) }
            return
        }
        lockCurrent = true
        // An event right after a segment boundary is mostly in the previous file.
        if (System.currentTimeMillis() - segmentStartMs < 30_000) previousSegment?.let { export(it) }
    }

    fun addTelemetry(speedKmh: Double, lat: Double?, lon: Double?, leanDeg: Double, gForce: Double) {
        if (recording == null) return
        val offset = System.currentTimeMillis() - segmentStartMs
        if (telemetry.isNotEmpty() && offset - telemetry.last().offsetMs < 900) return
        telemetry += TelemetryPoint(offset, speedKmh, lat, lon, leanDeg, gForce)
    }

    @SuppressLint("MissingPermission") // audio is never enabled
    private fun startSegment() {
        val name = "ride_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        val file = File(loopDir, name)
        segmentFile = file
        telemetry.clear()
        lockCurrent = false
        segmentStartMs = System.currentTimeMillis()
        recording = recorder
            .prepareRecording(context, FileOutputOptions.Builder(file).build())
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize) onFinalized(file, event)
            }
        main.postDelayed(rotate, segmentMs)
        onStateChanged(true)
    }

    private fun onFinalized(file: File, event: VideoRecordEvent.Finalize) {
        recording = null
        main.removeCallbacks(rotate)
        if (event.hasError() && event.error != VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED) {
            Log.w(TAG, "Segment finalised with error ${event.error}", event.cause)
        }
        if (event.error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE && keepGoing) {
            // Camera closed (app backgrounded / screen off). Resume when it is bound again.
            keepGoing = false
            interrupted = true
        }
        if (file.exists() && file.length() > 0) {
            File(file.path.removeSuffix(".mp4") + ".srt").writeText(TelemetrySrt.build(telemetry))
            if (lockCurrent) export(file)
            previousSegment = file
        }
        trimLoop()
        if (keepGoing) startSegment() else onStateChanged(false)
    }

    private fun trimLoop() {
        val files = loopDir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= loopBudgetBytes) break
            val base = f.path.substringBeforeLast('.')
            val inUse = listOfNotNull(segmentFile, previousSegment) + exporting
            if (inUse.any { it.path.substringBeforeLast('.') == base }) continue
            total -= f.length()
            f.delete()
        }
    }

    /** Copies a segment (and its .srt) into the shared gallery so loop trimming can't delete it. */
    private fun export(file: File) {
        if (!exporting.add(file)) return
        io.execute {
            try {
                val srt = File(file.path.removeSuffix(".mp4") + ".srt")
                copyToMediaStore(file, "video/mp4", MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "Movies/RiderAI")
                if (srt.exists()) {
                    copyToMediaStore(srt, "application/x-subrip", MediaStore.Downloads.EXTERNAL_CONTENT_URI, "Download/RiderAI")
                }
                main.post { onClipSaved(file.name) }
            } finally {
                exporting.remove(file)
            }
        }
    }

    fun release() {
        stop()
        io.shutdown()
    }

    private fun copyToMediaStore(src: File, mime: String, collection: android.net.Uri, relPath: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, src.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return
        try {
            resolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Export failed for ${src.name}", e)
            resolver.delete(uri, null, null)
        }
    }

    companion object { private const val TAG = "Dashcam" }
}
