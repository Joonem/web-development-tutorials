package com.riderai.safety

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.tan

/**
 * One detected road object in a camera frame, in normalised image coordinates (0..1).
 * Kept free of Android / ML Kit types so the risk logic can be unit tested on the JVM.
 */
data class TrackedBox(
    val trackingId: Int,
    val label: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val height: Float get() = bottom - top
    val width: Float get() = right - left
    val centerX: Float get() = (left + right) / 2f
}

enum class AlertLevel { NONE, CAUTION, WARNING, CRITICAL }

data class CollisionAssessment(
    val level: AlertLevel,
    /** Time to collision in seconds, from the expansion rate of the closest in-path object. */
    val ttcSeconds: Double?,
    /** Rough monocular distance estimate in metres. */
    val distanceMeters: Double?,
    /** Time headway (distance / own speed) in seconds. */
    val headwaySeconds: Double?,
    val label: String?,
)

/**
 * Forward Collision Warning + Headway Monitoring, modelled on the warning functions that
 * NCAP-style assessments look for (FCW, headway / following-distance alert).
 *
 * The camera is monocular, so:
 *  - TTC is estimated from how fast an object's image grows (TTC ≈ h / (dh/dt)), which needs
 *    no distance calibration;
 *  - distance is a rough estimate from an assumed real-world object height and the camera's
 *    vertical field of view. It is only used for the headway (tailgating) caution.
 */
class CollisionRiskEstimator(
    private val verticalFovDegrees: Double = 60.0,
    private val warningTtc: Double = 2.7,
    private val criticalTtc: Double = 1.5,
    private val minHeadway: Double = 1.0,
    private val minRiderSpeedKmh: Double = 12.0,
    private val historyWindowMs: Long = 900,
    /** Objects whose centre lies inside this horizontal band are considered "in path". */
    private val pathBand: ClosedFloatingPointRange<Float> = 0.30f..0.70f,
) {
    private data class Sample(val tMs: Long, val height: Float)

    private val history = HashMap<Int, ArrayDeque<Sample>>()

    fun reset() = history.clear()

    fun update(boxes: List<TrackedBox>, timestampMs: Long, riderSpeedKmh: Double): CollisionAssessment {
        // Record history for every tracked object and drop stale tracks.
        val seen = HashSet<Int>()
        for (b in boxes) {
            seen += b.trackingId
            val q = history.getOrPut(b.trackingId) { ArrayDeque() }
            q.addLast(Sample(timestampMs, b.height))
            while (q.isNotEmpty() && timestampMs - q.first().tMs > historyWindowMs) q.removeFirst()
        }
        history.keys.retainAll(seen)

        val inPath = boxes.filter { it.centerX in pathBand && it.height > 0.05f && it.bottom > 0.45f }
        val target = inPath.maxByOrNull { it.height }
            ?: return CollisionAssessment(AlertLevel.NONE, null, null, null, null)

        val ttc = history[target.trackingId]?.let { ttcFrom(it.toList()) }
        val distance = estimateDistance(target)
        val speedMs = riderSpeedKmh / 3.6
        val headway = if (speedMs > 0.5 && distance != null) distance / speedMs else null

        val moving = riderSpeedKmh >= minRiderSpeedKmh
        val level = when {
            !moving -> AlertLevel.NONE
            ttc != null && ttc < criticalTtc -> AlertLevel.CRITICAL
            ttc != null && ttc < warningTtc -> AlertLevel.WARNING
            headway != null && headway < minHeadway -> AlertLevel.CAUTION
            else -> AlertLevel.NONE
        }
        return CollisionAssessment(level, ttc, distance, headway, target.label)
    }

    /**
     * For a constant closing speed the image height is h(t) = k / (T - t), so 1/h falls
     * linearly with time and reaches zero at the moment of collision. A least-squares line
     * through 1/h therefore gives TTC = (fitted 1/h now) / -(slope), without the lag a fit on
     * h itself would have.
     */
    private fun ttcFrom(samples: List<Sample>): Double? {
        if (samples.size < 4) return null
        val spanMs = samples.last().tMs - samples.first().tMs
        if (spanMs < 250) return null
        val t0 = samples.first().tMs
        val xs = samples.map { (it.tMs - t0) / 1000.0 }
        val ys = samples.map { 1.0 / it.height.toDouble() }
        val mx = xs.average()
        val my = ys.average()
        var num = 0.0
        var den = 0.0
        for (i in xs.indices) {
            num += (xs[i] - mx) * (ys[i] - my)
            den += (xs[i] - mx) * (xs[i] - mx)
        }
        if (den == 0.0) return null
        val slope = num / den
        // Need the object to grow by at least ~2 % of its size per second; else it's noise.
        if (slope >= 0.0 || -slope < 0.02 * my) return null
        val nowInv = my + slope * (xs.last() - mx)
        if (nowInv <= 0.0) return 0.0
        return nowInv / -slope
    }

    private fun estimateDistance(box: TrackedBox): Double? {
        val realHeight = when (box.label.lowercase()) {
            "person", "pedestrian", "rider" -> 1.7
            "bicycle", "motorcycle", "motorbike" -> 1.3
            "truck", "bus" -> 3.0
            else -> 1.5 // passenger car
        }
        if (box.height <= 0f) return null
        val fovRad = Math.toRadians(verticalFovDegrees)
        // Height in image fraction -> angular size -> distance.
        val angular = box.height * fovRad
        return max(0.5, realHeight / tan(abs(angular)))
    }
}
