package com.riderai.voice

/** Commands the rider can speak. Anything else is forwarded to the AI assistant. */
sealed interface VoiceCommand {
    data object StartRecording : VoiceCommand
    data object StopRecording : VoiceCommand
    data object SaveClip : VoiceCommand
    data object ShowHud : VoiceCommand
    data object ShowCamera : VoiceCommand
    data object ToggleMirror : VoiceCommand
    data object WhatSpeed : VoiceCommand
    data object Status : VoiceCommand
    data object MuteAlerts : VoiceCommand
    data object UnmuteAlerts : VoiceCommand
    data object CancelSos : VoiceCommand
    data object Sos : VoiceCommand
    data object Help : VoiceCommand
    data class AskAssistant(val question: String) : VoiceCommand
}

/**
 * Turns a speech transcript into a [VoiceCommand].
 *
 * The wake word is optional when the app is in push-to-talk mode; in hands-free mode the
 * transcript must start with it ("rider, save clip"). Returns null when the wake word is
 * required but missing, so background chatter is ignored.
 */
class CommandParser(private val wakeWord: String = "rider") {

    fun parse(transcript: String, requireWakeWord: Boolean): VoiceCommand? {
        var text = normalise(transcript)
        if (text.isEmpty()) return null

        val wake = normalise(wakeWord)
        val wakeIdx = text.indexOf(wake)
        if (requireWakeWord) {
            if (wakeIdx < 0) return null
            text = text.substring(wakeIdx + wake.length).trim()
        } else if (text.startsWith(wake)) {
            text = text.removePrefix(wake).trim()
        }
        if (text.isEmpty()) return VoiceCommand.Help
        text = text.removePrefix("please ").trim()

        fun has(vararg phrases: String) = phrases.any { text.contains(it) }

        return when {
            has("cancel sos", "cancel emergency", "i'm ok", "i am ok", "im ok", "i'm okay", "i am okay", "false alarm") ->
                VoiceCommand.CancelSos
            has("sos", "emergency", "call for help", "help me") -> VoiceCommand.Sos
            has("save clip", "save video", "save that", "mark", "lock clip", "keep that") -> VoiceCommand.SaveClip
            has("stop recording", "stop dashcam", "stop camera recording") -> VoiceCommand.StopRecording
            has("start recording", "start dashcam", "record", "dashcam on") -> VoiceCommand.StartRecording
            has("mirror") -> VoiceCommand.ToggleMirror
            has("hud", "heads up", "head up") -> VoiceCommand.ShowHud
            has("camera view", "show camera", "dashcam view") -> VoiceCommand.ShowCamera
            has("unmute", "alerts on", "sound on") -> VoiceCommand.UnmuteAlerts
            has("mute", "quiet", "alerts off", "silence") -> VoiceCommand.MuteAlerts
            has("speed", "how fast") -> VoiceCommand.WhatSpeed
            has("status", "report") -> VoiceCommand.Status
            text == "help" || has("what can you do", "commands") -> VoiceCommand.Help
            else -> VoiceCommand.AskAssistant(transcript.trim())
        }
    }

    private fun normalise(s: String) =
        s.lowercase().replace(Regex("[,.!?]"), " ").replace(Regex("\\s+"), " ").trim()
}
