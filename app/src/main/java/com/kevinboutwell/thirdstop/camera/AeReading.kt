package com.kevinboutwell.thirdstop.camera

/**
 * One stable reflective-meter sample derived from the camera's auto-exposure
 * state. [ev100] is uncalibrated scene EV at ISO 100 (the per-mode calibration
 * offset is applied downstream).
 */
data class AeReading(
    val ev100: Double,
    val iso: Int,
    val exposureTimeSec: Double,
    val aperture: Double,
    val apertureIsFallback: Boolean,
    val converged: Boolean,
)
