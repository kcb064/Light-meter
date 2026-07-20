package com.kevinboutwell.thirdstop.camera

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Build
import android.util.SizeF
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
import com.kevinboutwell.thirdstop.core.Exposure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.sqrt

/** One camera lens the zoom UI can jump to, in 35mm-equivalent millimetres. */
data class LensInfo(val eqMm: Float, val name: String)

/** What the bound camera's zoom can do, all in 35mm-equivalent millimetres. */
data class ZoomCaps(
    /** Physical (or synthesized) lenses, sorted ascending by [LensInfo.eqMm]. */
    val lenses: List<LensInfo>,
    /** The main lens — 35mm-equivalent mm at zoomRatio 1.0. */
    val mainEqMm: Float,
    val minMm: Float,
    val maxMm: Float,
)

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

    /** Null until bound, or when the camera reports no usable focal-length data. */
    private val _zoomCaps = MutableStateFlow<ZoomCaps?>(null)
    val zoomCaps: StateFlow<ZoomCaps?> = _zoomCaps.asStateFlow()

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var fallbackAperture: Double? = null
    private var desiredZoomMm: Float? = null
    private var minZoomRatio = 1f
    private var maxZoomRatio = 1f
    private var bindFailed = false

    // Stability window, shared between the camera callback thread and the main
    // thread (resetStability via bind/meterAt/meterAverage) — guard with the lock.
    private val stabilityLock = Any()
    private val recentEvs = ArrayDeque<Double>()
    private var framesSinceAction = 0

    /**
     * Binds the preview and starts metering. Never throws (except cancellation):
     * failures surface through [unsupportedReason] so the UI can say something
     * instead of sitting on "settling" forever.
     */
    suspend fun bind(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        try {
            bindInternal(lifecycleOwner, previewView)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            bindFailed = true
            _unsupportedReason.value =
                "Couldn't start the camera — it may be in use by another app."
            _isSettling.value = false
        }
    }

    /**
     * Clears a previous bind failure so a returning viewfinder composes and
     * retries. Permanent hardware verdicts (LEGACY level) are kept.
     */
    fun clearBindError() {
        if (bindFailed) {
            bindFailed = false
            _unsupportedReason.value = null
        }
    }

    private suspend fun bindInternal(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
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
        bindFailed = false
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

        val zoomState = boundCamera.cameraInfo.zoomState.value
        minZoomRatio = zoomState?.minZoomRatio ?: 1f
        maxZoomRatio = zoomState?.maxZoomRatio
            ?: info.getCameraCharacteristic(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
            ?: 1f
        _zoomCaps.value = computeZoomCaps(info)
        // Re-apply the requested framing after every rebind (screen returns
        // reset CameraX zoom to 1.0).
        desiredZoomMm?.let { applyZoomMm(it) }
    }

    /**
     * Zooms so the frame matches [mm] (35mm-equivalent). Remembered and
     * re-applied on rebind. The caller clamps to [ZoomCaps]; the ratio is
     * additionally clamped to what the camera actually supports.
     */
    fun setZoomMm(mm: Float) {
        desiredZoomMm = mm
        applyZoomMm(mm)
    }

    private fun applyZoomMm(mm: Float) {
        val caps = _zoomCaps.value ?: return
        val ratio = (mm / caps.mainEqMm).coerceIn(minZoomRatio, maxZoomRatio)
        camera?.cameraControl?.setZoomRatio(ratio)
    }

    /**
     * Enumerates the device's rear lenses as 35mm-equivalent focal lengths.
     * Prefers per-lens data from the logical camera's physical IDs; falls back
     * to synthesizing an ultra-wide entry from the zoom-ratio range.
     */
    private fun computeZoomCaps(info: Camera2CameraInfo): ZoomCaps? {
        val mainEqMm = equivalentMm(
            info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.minOrNull(),
            info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE),
        ) ?: return null

        val physicalMms = mutableListOf<Float>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val physicalIds = runCatching {
                manager.getCameraCharacteristics(info.cameraId).physicalCameraIds
            }.getOrDefault(emptySet())
            for (id in physicalIds) {
                val chars = runCatching { manager.getCameraCharacteristics(id) }.getOrNull()
                    ?: continue
                if (chars.get(CameraCharacteristics.LENS_FACING) !=
                    CameraMetadata.LENS_FACING_BACK
                ) continue
                val eq = equivalentMm(
                    chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.minOrNull(),
                    chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE),
                ) ?: continue
                // Drop lenses the logical camera can't reach with setZoomRatio
                // (e.g. depth or macro modules).
                val ratio = eq / mainEqMm
                if (ratio < minZoomRatio - 0.05f || ratio > maxZoomRatio + 0.05f) continue
                physicalMms += eq
            }
        }
        if (physicalMms.isEmpty() && minZoomRatio < 0.95f) {
            physicalMms += mainEqMm * minZoomRatio
        }
        if (physicalMms.none { kotlin.math.abs(it - mainEqMm) <= 0.5f }) {
            physicalMms += mainEqMm
        }
        physicalMms.sort()
        val lenses = physicalMms
            .fold(mutableListOf<Float>()) { acc, mm ->
                if (acc.isEmpty() || mm - acc.last() > 0.5f) acc += mm
                acc
            }
            .map { mm ->
                val name = when {
                    mm < mainEqMm - 0.5f -> "ultra-wide"
                    mm > mainEqMm + 0.5f -> "telephoto"
                    else -> "wide"
                }
                LensInfo(eqMm = mm, name = name)
            }

        val minMm = lenses.first().eqMm.coerceAtLeast(mainEqMm * minZoomRatio)
        // 3x digital headroom past the longest lens, capped by the camera.
        val maxMm = (lenses.last().eqMm * DIGITAL_HEADROOM)
            .coerceAtMost(mainEqMm * maxZoomRatio)
            .coerceAtLeast(lenses.last().eqMm)
            .coerceAtLeast(minMm)
        return ZoomCaps(lenses = lenses, mainEqMm = mainEqMm, minMm = minMm, maxMm = maxMm)
    }

    /** Full-frame-equivalent focal length by diagonal crop factor. */
    private fun equivalentMm(focalMm: Float?, sensor: SizeF?): Float? {
        if (focalMm == null || focalMm <= 0f || sensor == null) return null
        val diagonal = sqrt(sensor.width * sensor.width + sensor.height * sensor.height)
        if (diagonal <= 0f) return null
        return focalMm * FULL_FRAME_DIAGONAL_MM / diagonal
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
        synchronized(stabilityLock) {
            recentEvs.clear()
            framesSinceAction = 0
        }
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

            val converged = aeState == CameraMetadata.CONTROL_AE_STATE_CONVERGED ||
                aeState == CameraMetadata.CONTROL_AE_STATE_LOCKED ||
                aeState == CameraMetadata.CONTROL_AE_STATE_FLASH_REQUIRED

            val exposureSec = Exposure.exposureTimeNsToSeconds(exposureNs)
            val effectiveIso = iso * (boost / 100.0)
            val ev100 = Exposure.ev100FromExposure(aperture, exposureSec, effectiveIso)

            val stable: Boolean
            val timedOut: Boolean
            val publishedEv: Double
            synchronized(stabilityLock) {
                framesSinceAction++
                if (converged) {
                    recentEvs.addLast(ev100)
                    while (recentEvs.size > STABILITY_WINDOW) recentEvs.removeFirst()
                } else {
                    recentEvs.clear()
                }
                stable = recentEvs.size == STABILITY_WINDOW &&
                    (recentEvs.max() - recentEvs.min()) <= STABILITY_TOLERANCE_EV
                timedOut = framesSinceAction > TIMEOUT_FRAMES
                publishedEv = if (stable) recentEvs.average() else ev100
            }

            if (stable || timedOut) {
                _reading.value = AeReading(
                    ev100 = publishedEv,
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
        const val FULL_FRAME_DIAGONAL_MM = 43.27f
        const val DIGITAL_HEADROOM = 3f
        const val STABILITY_WINDOW = 5
        const val STABILITY_TOLERANCE_EV = 0.2
        // ~2 s at 30 fps: publish an unconverged reading rather than hang.
        const val TIMEOUT_FRAMES = 60
    }
}
