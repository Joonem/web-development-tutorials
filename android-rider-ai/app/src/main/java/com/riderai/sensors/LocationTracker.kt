package com.riderai.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper

/** GPS speed, heading and trip distance via the platform LocationManager (no Play Services). */
class LocationTracker(context: Context, private val onUpdate: (Location) -> Unit) {
    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val listener = LocationListener { onUpdate(it) }
    private var running = false

    @SuppressLint("MissingPermission") // caller checks ACCESS_FINE_LOCATION first
    fun start() {
        if (running) return
        running = true
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
        }
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 0f, listener, Looper.getMainLooper())
        }
    }

    fun stop() {
        if (!running) return
        running = false
        lm.removeUpdates(listener)
    }
}
