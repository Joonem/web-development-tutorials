package com.riderai.safety

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

/** Sends the emergency message once an SOS countdown runs out (eCall-style). */
class SosManager(private val context: Context) {

    /** @return a short description of what was done, for the UI and voice feedback. */
    fun dispatch(contact: String, riderName: String, lat: Double?, lon: Double?, reason: String): String {
        if (contact.isBlank()) {
            openDialer("112")
            return "No emergency contact set. Opening the dialer."
        }
        val who = riderName.ifBlank { "A rider" }
        val where = if (lat != null && lon != null)
            "Location: https://maps.google.com/?q=$lat,$lon" else "Location unavailable."
        val text = "RIDER AI ALERT: $who may have crashed ($reason) and did not respond. $where"

        val canSms = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED
        return if (canSms) {
            try {
                val sms = context.getSystemService(SmsManager::class.java)
                sms.sendMultipartTextMessage(contact, null, sms.divideMessage(text), null, null)
                openDialer(contact)
                "Emergency text sent to $contact. Calling now."
            } catch (e: Exception) {
                openSmsComposer(contact, text)
                "Couldn't send automatically. The message is ready to send."
            }
        } else {
            openSmsComposer(contact, text)
            "SMS permission missing. The message is ready to send."
        }
    }

    private fun openDialer(number: String) {
        context.startActivity(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun openSmsComposer(number: String, body: String) {
        context.startActivity(
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))
                .putExtra("sms_body", body)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
