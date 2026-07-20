package com.kevinboutwell.thirdstop.core

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/**
 * Reciprocity-failure correction: maps a metered exposure time to the time the
 * film actually needs. Identity for short exposures; grows for long ones.
 */
sealed interface ReciprocityModel {

    /** Corrected exposure time in seconds for a metered time in seconds. */
    fun correct(meteredSec: Double): Double

    /** True when [correct] would meaningfully change this metered time. */
    fun applies(meteredSec: Double): Boolean = correct(meteredSec) > meteredSec * 1.01

    /**
     * Ilford's published power model: tc = tm^p for tm above [threshold] seconds,
     * identity below.
     */
    data class Power(val p: Double, val threshold: Double = 1.0) : ReciprocityModel {
        override fun correct(meteredSec: Double): Double =
            if (meteredSec <= threshold) meteredSec else meteredSec.pow(p)
    }

    /**
     * Piecewise schedule of (metered, corrected) anchor points taken from a
     * datasheet, interpolated in log-log space. Identity below the first anchor;
     * the last segment's slope is extrapolated beyond the final anchor.
     */
    data class Schedule(val points: List<Point>) : ReciprocityModel {
        data class Point(val metered: Double, val corrected: Double)

        init {
            require(points.size >= 2) { "schedule needs at least two anchor points" }
            require(points.zipWithNext().all { (a, b) -> a.metered < b.metered }) {
                "schedule anchors must be strictly increasing in metered time"
            }
        }

        override fun correct(meteredSec: Double): Double {
            if (meteredSec <= points.first().metered) {
                // Identity below the first anchor; the first anchor itself should be
                // an identity point (metered == corrected) for continuity.
                return if (meteredSec == points.first().metered) points.first().corrected else meteredSec
            }
            val (a, b) = points.zipWithNext().lastOrNull { (a, b) ->
                meteredSec > a.metered && meteredSec <= b.metered
            } ?: points.takeLast(2).let { it[0] to it[1] } // beyond last anchor: extrapolate
            val slope = ln(b.corrected / a.corrected) / ln(b.metered / a.metered)
            return a.corrected * exp(slope * ln(meteredSec / a.metered))
        }
    }

    /** Films with no practically relevant reciprocity failure in normal use. */
    data object None : ReciprocityModel {
        override fun correct(meteredSec: Double): Double = meteredSec
    }
}
