package com.riderai.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * Speech in and out for the helmet headset.
 *
 *  - Hands-free mode keeps the recogniser listening and restarts it after every result or
 *    timeout; transcripts must start with the wake word.
 *  - Push-to-talk listens once per [listenOnce] call; no wake word needed.
 *  - Listening pauses while the app is speaking so it does not hear itself.
 *
 * Must be used from the main thread.
 */
class VoiceEngine(
    context: Context,
    private val onTranscript: (text: String, fromHandsFree: Boolean) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit,
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
    private var recognizer: SpeechRecognizer? = null
    private var ttsReady = false
    private var speaking = 0
    private var listening = false
    private var oneShot = false

    var handsFree = false
        set(value) {
            field = value
            if (value) scheduleListen(200) else if (!oneShot) cancelListening()
        }

    private val tts = TextToSpeech(appContext) { status ->
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) configureTts()
    }

    val available: Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    private fun configureTts() {
        tts.language = Locale.getDefault()
        tts.setSpeechRate(1.05f)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) {
                main.post { onSpeechEnded() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                main.post { onSpeechEnded() }
            }
        })
    }

    private fun onSpeechEnded() {
        speaking = (speaking - 1).coerceAtLeast(0)
        if (speaking == 0 && handsFree) scheduleListen(300)
    }

    /** Speaks [text]; [urgent] interrupts anything already being said. */
    fun speak(text: String, urgent: Boolean = false) {
        if (!ttsReady || text.isBlank()) return
        cancelListening()
        speaking++
        val mode = if (urgent) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        if (urgent) speaking = 1
        val params = Bundle().apply { putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC) }
        tts.speak(text, mode, params, UUID.randomUUID().toString())
    }

    fun beep(critical: Boolean) {
        tone.startTone(
            if (critical) ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK else ToneGenerator.TONE_PROP_BEEP2,
            if (critical) 600 else 200,
        )
    }

    /** Push-to-talk. */
    fun listenOnce() {
        if (speaking > 0) tts.stop().also { speaking = 0 }
        oneShot = true
        startListening()
    }

    private fun scheduleListen(delayMs: Long) {
        main.removeCallbacks(listenRunnable)
        main.postDelayed(listenRunnable, delayMs)
    }

    private val listenRunnable = Runnable { if (handsFree && speaking == 0) startListening() }

    private fun startListening() {
        if (!available || listening) return
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
        }
        listening = true
        onListeningChanged(true)
        r.startListening(intent)
    }

    private fun cancelListening() {
        main.removeCallbacks(listenRunnable)
        if (listening) {
            recognizer?.cancel()
            listening = false
            onListeningChanged(false)
        }
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            val wasOneShot = oneShot
            finish()
            if (!text.isNullOrBlank()) onTranscript(text, !wasOneShot)
        }

        override fun onError(error: Int) {
            finish()
        }

        private fun finish() {
            listening = false
            oneShot = false
            onListeningChanged(false)
            if (handsFree && speaking == 0) scheduleListen(400)
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    fun release() {
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        tts.stop()
        tts.shutdown()
        tone.release()
    }
}
