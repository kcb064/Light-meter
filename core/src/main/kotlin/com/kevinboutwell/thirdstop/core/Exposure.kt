package com.kevinboutwell.thirdstop.core

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Photographic exposure math. All EV values are EV100 (EV at ISO 100) unless
 * a function explicitly takes an ISO.
 *
 * The exposure equation: N^2 / t = 2^EV_iso, where N is the f-number,
 * t the shutter time in seconds, and EV_iso = EV100 + log2(iso / 100).
 */
object Exposure {

    /** Scene EV100 implied by a camera auto-exposure result (reflective metering). */
    fun ev100FromExposure(aperture: Double, exposureSec: Double, iso: Double): Double =
        log2(aperture * aperture / exposureSec) - log2(iso / 100.0)

    /**
     * Incident EV100 from an illuminance reading.
     * EV100 = log2(lux / 2.5), from the incident calibration constant C = 250 lux·s.
     */
    fun ev100FromLux(lux: Double): Double = log2(lux / 2.5)

    /** Illuminance corresponding to an EV100 (inverse of [ev100FromLux]). */
    fun luxFromEv100(ev100: Double): Double = 2.5 * 2.0.pow(ev100)

    /** EV at a given film ISO. */
    fun evAtIso(ev100: Double, iso: Double): Double = ev100 + log2(iso / 100.0)

    /** Shutter time in seconds satisfying the exposure equation for a fixed aperture. */
    fun shutterFor(evIso: Double, aperture: Double): Double =
        aperture * aperture / 2.0.pow(evIso)

    /** F-number satisfying the exposure equation for a fixed shutter time. */
    fun apertureFor(evIso: Double, shutterSec: Double): Double =
        sqrt(shutterSec * 2.0.pow(evIso))

    /**
     * Camera2 reports SENSOR_EXPOSURE_TIME in nanoseconds. Keep the conversion in
     * exactly one place.
     */
    fun exposureTimeNsToSeconds(ns: Long): Double = ns / 1_000_000_000.0
}
