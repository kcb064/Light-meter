package com.kevinboutwell.thirdstop.core

import kotlin.math.log2

enum class Priority { APERTURE, SHUTTER }

/**
 * A solved exposure. Exactly one of aperture/shutter was computed (per
 * [Priority]); the other is the user's fixed choice.
 *
 * [correctedSeconds] is the reciprocity-adjusted exposure time, present only
 * when the film's model meaningfully changes the metered time. It is reported
 * as raw seconds (long exposures are hand-timed, not snapped to stops).
 */
data class Solution(
    val aperture: StopValue,
    val shutter: StopValue,
    val meteredSeconds: Double,
    val correctedSeconds: Double?,
    val outOfRange: Boolean,
)

object ExposureSolver {

    /**
     * Solve the exposure equation for the free variable.
     *
     * @param ev100 calibrated scene EV at ISO 100
     * @param ecStops exposure compensation in stops; positive = more exposure
     *   (longer shutter / wider aperture)
     * @param fixed the locked value: an aperture when [priority] is APERTURE,
     *   a shutter when SHUTTER
     */
    fun solve(
        ev100: Double,
        iso: StopValue,
        ecStops: Double,
        priority: Priority,
        fixed: StopValue,
        film: FilmStock = FilmStocks.NONE,
    ): Solution {
        val evIso = ev100 + log2(iso.exact / 100.0) - ecStops
        return when (priority) {
            Priority.APERTURE -> {
                val snapped = Stops.snapShutter(Exposure.shutterFor(evIso, fixed.exact))
                withReciprocity(
                    aperture = fixed,
                    shutter = snapped.value,
                    meteredSeconds = snapped.value.exact,
                    outOfRange = snapped.outOfRange,
                    film = film,
                )
            }
            Priority.SHUTTER -> {
                val snapped = Stops.snapAperture(Exposure.apertureFor(evIso, fixed.exact))
                withReciprocity(
                    aperture = snapped.value,
                    shutter = fixed,
                    meteredSeconds = fixed.exact,
                    outOfRange = snapped.outOfRange,
                    film = film,
                )
            }
        }
    }

    private fun withReciprocity(
        aperture: StopValue,
        shutter: StopValue,
        meteredSeconds: Double,
        outOfRange: Boolean,
        film: FilmStock,
    ): Solution {
        val corrected = film.reciprocity
            .takeIf { it.applies(meteredSeconds) }
            ?.correct(meteredSeconds)
        return Solution(aperture, shutter, meteredSeconds, corrected, outOfRange)
    }
}
