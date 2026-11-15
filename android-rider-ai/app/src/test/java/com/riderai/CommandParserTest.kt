package com.riderai

import com.riderai.voice.CommandParser
import com.riderai.voice.VoiceCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommandParserTest {
    private val p = CommandParser("rider")

    @Test fun wakeWordRequired() {
        assertNull(p.parse("save clip", requireWakeWord = true))
        assertEquals(VoiceCommand.SaveClip, p.parse("Rider, save clip!", requireWakeWord = true))
        assertEquals(VoiceCommand.SaveClip, p.parse("okay rider save that", requireWakeWord = true))
    }

    @Test fun pushToTalkWithoutWakeWord() {
        assertEquals(VoiceCommand.StartRecording, p.parse("start recording", false))
        assertEquals(VoiceCommand.StopRecording, p.parse("stop recording", false))
        assertEquals(VoiceCommand.WhatSpeed, p.parse("how fast am I going", false))
        assertEquals(VoiceCommand.ShowHud, p.parse("HUD mode", false))
        assertEquals(VoiceCommand.ToggleMirror, p.parse("mirror the display", false))
        assertEquals(VoiceCommand.UnmuteAlerts, p.parse("unmute", false))
        assertEquals(VoiceCommand.MuteAlerts, p.parse("mute alerts", false))
    }

    @Test fun sosAndCancel() {
        assertEquals(VoiceCommand.Sos, p.parse("rider SOS", true))
        assertEquals(VoiceCommand.CancelSos, p.parse("rider I'm OK", true))
        assertEquals(VoiceCommand.CancelSos, p.parse("cancel emergency", false))
    }

    @Test fun otherSpeechGoesToAssistant() {
        val c = p.parse("rider is it going to rain later", true)
        assertEquals(VoiceCommand.AskAssistant("rider is it going to rain later"), c)
    }

    @Test fun wakeWordAloneIsHelp() {
        assertEquals(VoiceCommand.Help, p.parse("rider", true))
    }
}
