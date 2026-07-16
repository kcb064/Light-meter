package com.kevinboutwell.lightmeter.camera

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.kevinboutwell.lightmeter.core.Exposure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reflective metering through the phone camera. Binds only a [Preview] use
 * case and observes the auto-exposure algorithm's converged state via Camera2
 * capture metadata — no photos are ever taken.
 *
 * All Camera2 interop (an @Experimental CameraX surface, stable in practice)
 * is contained in this class.
 */
@OptIn(ExperimentalCamera2Interop::class)
class CameraMeter(private val context: Context) {

    private val _reading = MutableStateFlow<AeReading?>(null)
    /** Latest stable (or timed-out) sample. Null until the first one lands. */
    val reading: StateFlow<AeReading?> = _reading.asStateFlow()

    private val _isSettling = MutableStateFlow(true)
    val isSettling: StateFlow<Boolean> = _isSettling.asStateFlow()

    private val _supportsSpot = MutableStateFlow(false)
    val supportsSpot: StateFlow<Boolean> = _supportsSpot.asStateFlow()

    /** Non-null when reflective metering can't work on this device. */
    private val _unsupportedReason = MutableStateFlow<String?>(null)
    val unsupportedReason: StateFlow<String?> = _unsupportedReason.asStateFlow()

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var fallbackAperture: Double? = null

    // Stability window, touched only from the camera callback thread.
    private val recentEvs = ArrayDeque<Double>()
    private var framesSinceAction = 0

    suspend fun bind(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val cameraProvider = awaitProvider()
        provider = cameraProvider

        val previewBuilder = Preview.Builder()
        Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(captureCallback)
        val preview = previewBuilder.build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        cameraProvider.unbindAll()
        val boundCamera = cameraProvider.bindToLifecycle(
            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview,
        )
        camera = boundCamera
        resetStability()

        val info = Camera2CameraInfo.from(boundCamera.cameraInfo)
        val hardwareLevel = info.getCameraCharacteristic(
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL,
        )
        if (hardwareLevel == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            _unsupportedReason.value =
                "This device's camera doesn't report exposure metadata. Use incident mode."
        } else {
            _unsupportedReason.value = null
        }
        _supportsSpot.value =
            (info.getCameraCharacteristic(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0) > 0
        fallbackAperture = info.getCameraCharacteristic(
            CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES,
        )?.firstOrNull()?.toDouble()

        // The app's exposure-compensation dial lives in the math layer; the
        // camera's AE compensation must never pollute readings.
        boundCamera.cameraControl.setExposureCompensationIndex(0)
    }

    fun unbind() {
        provider?.unbindAll()
        camera = null
        _reading.value = null
        _isSettling.value = true
    }

    /** Spot-meter at a point from the viewfinder's [MeteringPoint] factory. */
    fun meterAt(point: MeteringPoint) {
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AE)
            .disableAutoCancel()
            .build()
        camera?.cameraControl?.startFocusAndMetering(action)
        resetStability()
    }

    /** Return to default frame-wide (matrix/average) metering. */
    fun meterAverage() {
        camera?.cameraControl?.cancelFocusAndMetering()
        resetStability()
    }

    private fun resetStability() {
        recentEvs.clear()
        framesSinceAction = 0
        _isSettling.value = true
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult,
        ) {
            val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: return
            val exposureNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: return
            val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
            val boost = result.get(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST) ?: 100
            val reportedAperture = result.get(CaptureResult.LENS_APERTURE)?.toDouble()

            // Phones are effectively fixed-aperture, but logical multi-camera
            // devices can switch lenses mid-stream, so read it per frame.
            val aperture = reportedAperture ?: fallbackAperture ?: DEFAULT_APERTURE
            val apertureIsFallback = reportedAperture == null

            framesSinceAction++
            val converged = aeState == CameraMetadata.CONTROL_AE_STATE_CONVERGED ||
                aeState == CameraMetadata.CONTROL_AE_STATE_LOCKED ||
                aeState == CameraMetadata.CONTROL_AE_STATE_FLASH_REQUIRED

            val exposureSec = Exposure.exposureTimeNsToSeconds(exposureNs)
            val effectiveIso = iso * (boost / 100.0)
            val ev100 = Exposure.ev100FromExposure(aperture, exposureSec, effectiveIso)

            if (converged) {
                recentEvs.addLast(ev100)
                while (recentEvs.size > STABILITY_WINDOW) recentEvs.removeFirst()
            } else {
                recentEvs.clear()
            }

            val stable = recentEvs.size == STABILITY_WINDOW &&
                (recentEvs.max() - recentEvs.min()) <= STABILITY_TOLERANCE_EV
            val timedOut = framesSinceAction > TIMEOUT_FRAMES

            if (stable || timedOut) {
                _reading.value = AeReading(
                    ev100 = if (stable) recentEvs.average() else ev100,
                    iso = iso,
                    exposureTimeSec = exposureSec,
                    aperture = aperture,
                    apertureIsFallback = apertureIsFallback,
                    converged = stable,
                )
                _isSettling.value = false
            }
        }
    }

    private suspend fun awaitProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    try {
                        cont.resume(future.get())
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        }

    private companion object {
        const val DEFAULT_APERTURE = 1.8
        const val STABILITY_WINDOW = 5
        const val STABILITY_TOLERANCE_EV = 0.2
        // ~2 s at 30 fps: publish an unconverged reading rather than hang.
        const val TIMEOUT_FRAMES = 60
    }
}
