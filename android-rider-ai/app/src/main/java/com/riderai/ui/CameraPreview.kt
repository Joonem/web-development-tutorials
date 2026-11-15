package com.riderai.ui

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.riderai.RideViewModel

/**
 * Binds preview + collision-warning analysis + dashcam video to the back camera.
 * Devices that can't run all three at once fall back to fewer use cases.
 */
@Composable
fun CameraPreview(vm: RideViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val p = future.get()
            provider = p
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val selector = CameraSelector.DEFAULT_BACK_CAMERA
            val combos = listOf(
                listOf(preview, vm.imageAnalysis, vm.dashcam.videoCapture),
                listOf(preview, vm.dashcam.videoCapture),
                listOf(preview, vm.imageAnalysis),
            )
            for (useCases in combos) {
                try {
                    p.unbindAll()
                    p.bindToLifecycle(owner, selector, *useCases.toTypedArray())
                    if (vm.dashcam.videoCapture in useCases) vm.onCameraBound()
                    break
                } catch (e: Exception) {
                    Log.w("CameraPreview", "Use-case combination not supported, trying fewer", e)
                }
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose { provider?.unbindAll() }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
