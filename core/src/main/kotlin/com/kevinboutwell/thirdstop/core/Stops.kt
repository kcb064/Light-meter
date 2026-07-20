package com.kevinboutwell.thirdstop.core

import java.util.Locale
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * A settable value on the third-stop lattice: the conventional photographic
 * label plus the exact value used in computation. E.g. nominal "1/125" has
 * exact 2^-7 s = 1/128 s; nominal "15\"" has exact 16 s.
 */
data class StopValue(val nominal: String, val exact: Double)

data class Snapped(val value: StopValue, val index: Int, val outOfRange: Boolean)

/**
 * Third-stop tables for aperture, shutter and ISO. Exact values are generated
 * from the lattice (2^(i/3) per stop-third) so index arithmetic is exact;
 * labels follow photographic convention.
 */
object Stops {

    private val APERTURE_LABELS = listOf(
        "1.0", "1.1", "1.2", "1.4", "1.6", "1.8", "2", "2.2", "2.5", "2.8",
        "3.2", "3.5", "4", "4.5", "5.0", "5.6", "6.3", "7.1", "8", "9",
        "10", "11", "13", "14", "16", "18", "20", "22", "25", "29",
        "32", "36", "40", "45", "51", "57", "64",
    )

    /** f-numbers f/1.0 .. f/64; exact_i = 2^(i/6) (N doubles every two stops). */
    val APERTURES: List<StopValue> =
        APERTURE_LABELS.mapIndexed { i, label -> StopValue(label, 2.0.pow(i / 6.0)) }

    // Index k on the shutter lattice runs -39..18 relative to 1 s; exact = 2^(k/3).
    private const val SHUTTER_K_MIN = -39

    private val SHUTTER_LABELS = listOf(
        "1/8000", "1/6400", "1/5000", "1/4000", "1/3200", "1/2500", "1/2000",
        "1/1600", "1/1250", "1/1000", "1/800", "1/640", "1/500", "1/400",
        "1/320", "1/250", "1/200", "1/160", "1/125", "1/100", "1/80", "1/60",
        "1/50", "1/40", "1/30", "1/25", "1/20", "1/15", "1/13", "1/10",
        "1/8", "1/6", "1/5", "1/4", "0.3\"", "0.4\"", "0.5\"", "0.6\"", "0.8\"",
        "1\"", "1.3\"", "1.6\"", "2\"", "2.5\"", "3.2\"", "4\"", "5\"", "6\"",
        "8\"", "10\"", "13\"", "15\"", "20\"", "25\"", "30\"", "40\"", "50\"", "60\"",
    )

    /** Shutter times 1/8000 .. 60 s; exact_i = 2^((i + SHUTTER_K_MIN)/3) seconds. */
    val SHUTTERS: List<StopValue> =
        SHUTTER_LABELS.mapIndexed { i, label -> StopValue(label, 2.0.pow((i + SHUTTER_K_MIN) / 3.0)) }

    private val ISO_LABELS = listOf(
        "25", "32", "40", "50", "64", "80", "100", "125", "160", "200",
        "250", "320", "400", "500", "640", "800", "1000", "1250", "1600",
        "2000", "2500", "3200", "4000", "5000", "6400",
    )

    /** Film speeds ISO 25 .. 6400; exact_i = 25 * 2^(i/3). */
    val ISOS: List<StopValue> =
        ISO_LABELS.mapIndexed { i, label -> StopValue(label, 25.0 * 2.0.pow(i / 3.0)) }

    val ISO_100: StopValue = ISOS[6]
    val DEFAULT_APERTURE: StopValue = APERTURES.first { it.nominal == "5.6" }
    val DEFAULT_SHUTTER: StopValue = SHUTTERS.first { it.nominal == "1/125" }

    /**
     * Non-finite or non-positive inputs (an extreme EV overflowing 2^ev to
     * Infinity upstream, or zero from the inverse) clamp to the matching end of
     * the table instead of throwing: +Infinity to the top, everything else
     * (zero, negative, NaN) to the bottom.
     */
    private fun degenerateSnap(value: Double, table: List<StopValue>): Snapped? = when {
        value.isFinite() && value > 0 -> null
        value > 0 -> Snapped(table.last(), table.lastIndex, true)
        else -> Snapped(table.first(), 0, true)
    }

    /** Snap a shutter time in seconds to the nearest third-stop value (log space). */
    fun snapShutter(seconds: Double): Snapped {
        degenerateSnap(seconds, SHUTTERS)?.let { return it }
        val k = (3.0 * log2(seconds)).roundToInt()
        val index = k - SHUTTER_K_MIN
        val clamped = index.coerceIn(0, SHUTTERS.lastIndex)
        return Snapped(SHUTTERS[clamped], clamped, index != clamped)
    }

    /** Snap an f-number to the nearest third-stop value (log space). */
    fun snapAperture(n: Double): Snapped {
        degenerateSnap(n, APERTURES)?.let { return it }
        val index = (6.0 * log2(n)).roundToInt()
        val clamped = index.coerceIn(0, APERTURES.lastIndex)
        return Snapped(APERTURES[clamped], clamped, index != clamped)
    }

    /** Snap an ISO value to the nearest third-stop value (log space). */
    fun snapIso(iso: Double): Snapped {
        degenerateSnap(iso, ISOS)?.let { return it }
        val index = (3.0 * log2(iso / 25.0)).roundToInt()
        val clamped = index.coerceIn(0, ISOS.lastIndex)
        return Snapped(ISOS[clamped], clamped, index != clamped)
    }

    /** Format a corrected long-exposure time in seconds for display, e.g. "35s" or "2m 05s". */
    fun formatSeconds(seconds: Double): String = when {
        seconds < 1.0 -> "1/${(1.0 / seconds).roundToInt()}"
        seconds < 10.0 -> {
            val rounded = (seconds * 10).roundToInt() / 10.0
            if (rounded == rounded.toInt().toDouble()) "${rounded.toInt()}s" else "${rounded}s"
        }
        else -> {
            // Round before choosing a format so 119.6s becomes "2m 00s", not "120s".
            val total = seconds.roundToInt()
            if (total < 120) "${total}s"
            else String.format(Locale.ROOT, "%dm %02ds", total / 60, total % 60)
        }
    }
}
