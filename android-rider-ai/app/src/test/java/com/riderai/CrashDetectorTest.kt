package com.riderai

import com.riderai.safety.CrashDetector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CrashDetectorTest {
    private val g = 9.81f

    @Test fun impactThenStillnessFiresOnce() {
        val d = CrashDetector()
        assertFalse(d.onSample(0f, 5 * g, 0f, 0.0, 0))
        var fired = 0
        for (t in 100L..6000L step 100) if (d.onSample(0f, 0f, g, 0.0, t)) fired++
        assertEquals(1, fired)
    }

    @Test fun impactThenBikeOnItsSideFires() {
        val d = CrashDetector()
        d.onSample(0f, 6 * g, 0f, 0.0, 0)
        var fired = false
        // Still shaking (not still), but lying at 85°.
        for (t in 100L..3000L step 100) {
            val shake = if (t % 200 == 0L) 1.5f else 0.5f
            fired = fired || d.onSample(g * shake, 0f, 0f, 85.0, t)
        }
        assertEquals(true, fired)
    }

    @Test fun potholeWithoutStillnessDoesNotFire() {
        val d = CrashDetector()
        d.onSample(0f, 4.5f * g, 0f, 0.0, 0)
        var fired = false
        for (t in 100L..12_000L step 100) {
            val riding = if (t % 300 == 0L) 1.4f else 0.7f // normal riding vibration
            fired = fired || d.onSample(0f, g * riding, 0f, 15.0, t)
        }
        assertFalse(fired)
    }

    @Test fun leanFromGravity() {
        assertEquals(0.0, CrashDetector.leanFromGravity(0f, g), 0.01)
        assertEquals(45.0, CrashDetector.leanFromGravity(g, g), 0.01)
    }
}
