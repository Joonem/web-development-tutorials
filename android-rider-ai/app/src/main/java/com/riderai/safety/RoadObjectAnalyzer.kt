package com.riderai.safety

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

/**
 * Runs ML Kit's on-device object detector in stream mode (which keeps tracking IDs across
 * frames) and hands normalised, label-agnostic boxes to [onBoxes].
 *
 * ML Kit's bundled model finds prominent objects but does not classify vehicles; the
 * collision estimator only needs consistent boxes and tracking IDs. To get vehicle labels,
 * swap in a custom COCO model with `CustomObjectDetectorOptions`.
 */
class RoadObjectAnalyzer(
    private val onBoxes: (boxes: List<TrackedBox>, timestampMs: Long) -> Unit,
) : ImageAnalysis.Analyzer {

    private val detector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
    )

    @Volatile var enabled = true

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (!enabled || media == null) {
            proxy.close()
            return
        }
        val rotation = proxy.imageInfo.rotationDegrees
        val (w, h) = if (rotation == 90 || rotation == 270) proxy.height to proxy.width
                     else proxy.width to proxy.height
        val ts = System.currentTimeMillis()
        detector.process(InputImage.fromMediaImage(media, rotation))
            .addOnSuccessListener { objects ->
                val boxes = objects.mapNotNull { o ->
                    val id = o.trackingId ?: return@mapNotNull null
                    val r = o.boundingBox
                    TrackedBox(
                        trackingId = id,
                        label = o.labels.maxByOrNull { it.confidence }?.text ?: "object",
                        left = r.left / w.toFloat(),
                        top = r.top / h.toFloat(),
                        right = r.right / w.toFloat(),
                        bottom = r.bottom / h.toFloat(),
                    )
                }
                onBoxes(boxes, ts)
            }
            .addOnCompleteListener { proxy.close() }
    }

    fun close() = detector.close()
}
