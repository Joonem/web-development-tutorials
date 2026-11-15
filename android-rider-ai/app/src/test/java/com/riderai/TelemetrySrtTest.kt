package com.riderai

import com.riderai.dashcam.TelemetryPoint
import com.riderai.dashcam.TelemetrySrt
import kotlin.test.Test
import kotlin.test.assertTrue

class TelemetrySrtTest {
    @Test fun formatsCues() {
        val srt = TelemetrySrt.build(listOf(
            TelemetryPoint(0, 42.4, 1.0, 2.0, 12.0, 1.1),
            TelemetryPoint(1000, 50.0, null, null, -5.0, 1.0),
        ))
        assertTrue(srt.startsWith("1\n00:00:00,000 --> 00:00:01,000\n42 km/h"), srt)
        assertTrue(srt.contains("2\n00:00:01,000 --> 00:00:02,000\n50 km/h"), srt)
        assertTrue(srt.contains("1.00000, 2.00000"))
    }
}
