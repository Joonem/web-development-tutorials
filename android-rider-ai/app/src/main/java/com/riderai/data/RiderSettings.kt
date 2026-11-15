package com.riderai.data

import android.content.Context
import androidx.core.content.edit

/** User preferences, stored privately on the device. */
data class RiderPrefs(
    val apiKey: String = "",
    val speedLimitKmh: Int = 0,
    val maxLeanDeg: Int = 45,
    val emergencyContact: String = "",
    val riderName: String = "",
    val wakeWord: String = "rider",
    val handsFree: Boolean = true,
    val segmentMinutes: Int = 3,
    val loopStorageMb: Int = 4096,
    val autoRecord: Boolean = true,
    val collisionWarning: Boolean = true,
    val crashDetection: Boolean = true,
    val sosCountdownSec: Int = 30,
    val leanOffsetDeg: Float = 0f,
    val cameraVFovDeg: Float = 60f,
)

class RiderSettings(context: Context) {
    private val sp = context.getSharedPreferences("rider_prefs", Context.MODE_PRIVATE)

    fun load() = RiderPrefs(
        apiKey = sp.getString("apiKey", "") ?: "",
        speedLimitKmh = sp.getInt("speedLimitKmh", 0),
        maxLeanDeg = sp.getInt("maxLeanDeg", 45),
        emergencyContact = sp.getString("emergencyContact", "") ?: "",
        riderName = sp.getString("riderName", "") ?: "",
        wakeWord = sp.getString("wakeWord", "rider") ?: "rider",
        handsFree = sp.getBoolean("handsFree", true),
        segmentMinutes = sp.getInt("segmentMinutes", 3),
        loopStorageMb = sp.getInt("loopStorageMb", 4096),
        autoRecord = sp.getBoolean("autoRecord", true),
        collisionWarning = sp.getBoolean("collisionWarning", true),
        crashDetection = sp.getBoolean("crashDetection", true),
        sosCountdownSec = sp.getInt("sosCountdownSec", 30),
        leanOffsetDeg = sp.getFloat("leanOffsetDeg", 0f),
        cameraVFovDeg = sp.getFloat("cameraVFovDeg", 60f),
    )

    fun save(p: RiderPrefs) = sp.edit {
        putString("apiKey", p.apiKey.trim())
        putInt("speedLimitKmh", p.speedLimitKmh)
        putInt("maxLeanDeg", p.maxLeanDeg)
        putString("emergencyContact", p.emergencyContact.trim())
        putString("riderName", p.riderName.trim())
        putString("wakeWord", p.wakeWord.trim().ifEmpty { "rider" })
        putBoolean("handsFree", p.handsFree)
        putInt("segmentMinutes", p.segmentMinutes.coerceIn(1, 15))
        putInt("loopStorageMb", p.loopStorageMb.coerceAtLeast(256))
        putBoolean("autoRecord", p.autoRecord)
        putBoolean("collisionWarning", p.collisionWarning)
        putBoolean("crashDetection", p.crashDetection)
        putInt("sosCountdownSec", p.sosCountdownSec.coerceIn(10, 120))
        putFloat("leanOffsetDeg", p.leanOffsetDeg)
        putFloat("cameraVFovDeg", p.cameraVFovDeg.coerceIn(30f, 120f))
    }
}
