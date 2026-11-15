package com.riderai

import com.riderai.safety.AlertLevel
import com.riderai.safety.CollisionRiskEstimator
import com.riderai.safety.TrackedBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CollisionRiskTest {
    private fun box(h: Float, cx: Float = 0.5f, id: Int = 1) =
        TrackedBox(id, "car", cx - 0.1f, 0.9f - h, cx + 0.1f, 0.9f)

    /** Feeds an object whose image height grows so that true TTC is [ttc] seconds. */
    private fun approach(est: CollisionRiskEstimator, ttc: Double, speed: Double, cx: Float = 0.5f) =
        (0..10).map { i ->
            val t = i * 0.08 // seconds
            // h(t) = h0 * T / (T - t) for constant closing speed; TTC now = T - t
            val h = (0.15 * ttc / (ttc - t)).toFloat()
            est.update(listOf(box(h, cx)), (t * 1000).toLong(), speed)
        }.last()

    @Test fun fastApproachIsCritical() {
        val r = approach(CollisionRiskEstimator(), ttc = 2.0, speed = 50.0)
        assertEquals(AlertLevel.CRITICAL, r.level)
        assertNotNull(r.ttcSeconds)
        assertTrue(r.ttcSeconds!! in 0.8..1.6, "ttc=${r.ttcSeconds}")
    }

    @Test fun moderateApproachIsWarning() {
        val r = approach(CollisionRiskEstimator(), ttc = 3.2, speed = 50.0)
        assertEquals(AlertLevel.WARNING, r.level)
    }

    @Test fun noAlertWhenStopped() {
        val r = approach(CollisionRiskEstimator(), ttc = 2.0, speed = 3.0)
        assertEquals(AlertLevel.NONE, r.level)
    }

    @Test fun ignoresObjectsOutsideOurPath() {
        val r = approach(CollisionRiskEstimator(), ttc = 2.0, speed = 50.0, cx = 0.9f)
        assertEquals(AlertLevel.NONE, r.level)
    }

    @Test fun steadyCloseObjectGivesHeadwayCaution() {
        val est = CollisionRiskEstimator()
        var last = est.update(listOf(box(0.4f)), 0, 60.0)
        for (i in 1..10) last = est.update(listOf(box(0.4f)), i * 80L, 60.0)
        assertEquals(AlertLevel.CAUTION, last.level)
        assertTrue(last.headwaySeconds!! < 1.0)
    }
}
