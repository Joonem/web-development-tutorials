package com.riderai.safety

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Detects a probable crash / fall from the phone's motion sensors:
 *  1. an impact spike (total acceleration above [impactG]), followed by
 *  2. the bike lying over (lean beyond [lyingDownLeanDeg] for half of [stillWindowMs]) or the
 *     device being nearly still for [stillWindowMs].
 *
 * When both happen the detector fires once; the app then runs an SOS countdown the rider can
 * cancel by voice or on screen (an "eCall"-style automatic emergency call).
 */
class CrashDetector(
    private val impactG: Double = 4.0,
    private val lyingDownLeanDeg: Double = 70.0,
    private val stillToleranceG: Double = 0.15,
    private val stillWindowMs: Long = 4000,
    private val confirmWithinMs: Long = 10_000,
) {
    private var impactAtMs: Long? = null
    private var stillSinceMs: Long? = null
    private var lyingSinceMs: Long? = null
    private var fired = false

    fun reset() {
        impactAtMs = null
        stillSinceMs = null
        lyingSinceMs = null
        fired = false
    }

    /**
     * @param ax, ay, az accelerometer reading in m/s² (including gravity).
     * @param leanDeg current lean angle in degrees (signed, 0 = upright).
     * @return true exactly once when a crash is confirmed.
     */
    fun onSample(ax: Float, ay: Float, az: Float, leanDeg: Double, timestampMs: Long): Boolean {
        if (fired) return false
        val g = sqrt((ax * ax + ay * ay + az * az).toDouble()) / GRAVITY

        if (g >= impactG) {
            impactAtMs = timestampMs
            stillSinceMs = null
            lyingSinceMs = null
            return false
        }
        val impact = impactAtMs ?: return false
        if (timestampMs - impact > confirmWithinMs) {
            impactAtMs = null
            return false
        }

        if (abs(g - 1.0) < stillToleranceG) {
            if (stillSinceMs == null) stillSinceMs = timestampMs
        } else {
            stillSinceMs = null
        }
        if (abs(leanDeg) >= lyingDownLeanDeg) {
            if (lyingSinceMs == null) lyingSinceMs = timestampMs
        } else {
            lyingSinceMs = null
        }
        // Either the bike stays on its side, or the phone lies motionless, after the impact.
        val lyingLongEnough = lyingSinceMs?.let { timestampMs - it >= stillWindowMs / 2 } ?: false
        val stillLongEnough = stillSinceMs?.let { timestampMs - it >= stillWindowMs } ?: false
        if (lyingLongEnough || stillLongEnough) {
            fired = true
            return true
        }
        return false
    }

    companion object {
        const val GRAVITY = 9.80665

        /**
         * Lean (roll) angle from a gravity vector, assuming the phone is mounted in portrait
         * on the handlebar facing the rider/road: x is to the right, y is up.
         */
        fun leanFromGravity(gx: Float, gy: Float): Double =
            Math.toDegrees(atan2(gx.toDouble(), gy.toDouble()))
    }
}
