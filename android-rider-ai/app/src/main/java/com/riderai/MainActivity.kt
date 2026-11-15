package com.riderai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.riderai.ui.RiderApp
import com.riderai.ui.RiderTheme

class MainActivity : ComponentActivity() {

    private val vm: RideViewModel by viewModels()
    private val cameraGranted = mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onPermissionsResult() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The phone is the HUD/dashcam: keep the screen on while riding.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            RiderTheme {
                RiderApp(
                    vm = vm,
                    cameraGranted = cameraGranted.value,
                    onRequestPermissions = ::requestPermissions,
                )
            }
        }
        if (granted(Manifest.permission.CAMERA)) onPermissionsResult() else requestPermissions()
    }

    private fun requestPermissions() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.SEND_SMS,
            )
        )
    }

    private fun onPermissionsResult() {
        cameraGranted.value = granted(Manifest.permission.CAMERA)
        vm.start(
            hasLocation = granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
                granted(Manifest.permission.ACCESS_COARSE_LOCATION),
            hasMic = granted(Manifest.permission.RECORD_AUDIO),
        )
    }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
}
