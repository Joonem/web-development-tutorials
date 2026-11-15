package com.riderai.dashcam

import java.util.Locale

/** One second of ride telemetry, written next to each dashcam clip as a .srt subtitle track. */
data class TelemetryPoint(
    val offsetMs: Long,
    val speedKmh: Double,
    val lat: Double?,
    val lon: Double?,
    val leanDeg: Double,
    val gForce: Double,
)

/** Builds an SRT file so any video player shows speed / GPS / lean over the dashcam footage. */
object TelemetrySrt {
    fun build(points: List<TelemetryPoint>): String = buildString {
        points.forEachIndexed { i, p ->
            val end = points.getOrNull(i + 1)?.offsetMs ?: (p.offsetMs + 1000)
            append(i + 1).append('\n')
            append(ts(p.offsetMs)).append(" --> ").append(ts(end)).append('\n')
            append(String.format(Locale.US, "%.0f km/h  lean %.0f°  %.1f g", p.speedKmh, p.leanDeg, p.gForce))
            if (p.lat != null && p.lon != null) {
                append('\n').append(String.format(Locale.US, "%.5f, %.5f", p.lat, p.lon))
            }
            append("\n\n")
        }
    }

    private fun ts(ms: Long): String {
        val h = ms / 3_600_000
        val m = ms / 60_000 % 60
        val s = ms / 1000 % 60
        val milli = ms % 1000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, milli)
    }
}
