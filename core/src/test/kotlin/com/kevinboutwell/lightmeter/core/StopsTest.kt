package com.kevinboutwell.lightmeter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log2

class StopsTest {

    @Test
    fun `tables are strictly monotonic in exact value`() {
        for (table in listOf(Stops.APERTURES, Stops.SHUTTERS, Stops.ISOS)) {
            table.zipWithNext().forEach { (a, b) ->
                assertTrue("${a.nominal} < ${b.nominal}", a.exact < b.exact)
            }
        }
    }

    @Test
    fun `every nominal label is within a sixth stop of its exact value`() {
        // Self-consistency: the conventional label should imply roughly the value
        // the lattice assigns to it (photographic rounding is at most ~1/6 stop,
        // e.g. nominal 1/8000 vs exact 1/8192).
        Stops.APERTURES.forEach { v ->
            val implied = v.nominal.toDouble()
            val deltaStops = abs(2 * log2(v.exact / implied)) // aperture: 2 stops per doubling of N... inverse: stops = 2*log2(N)
            assertTrue("f/${v.nominal}: off by $deltaStops stops", deltaStops <= 0.17)
        }
        Stops.SHUTTERS.forEach { v ->
            val label = v.nominal.removeSuffix("\"")
            val implied = if (label.startsWith("1/")) 1.0 / label.substring(2).toDouble() else label.toDouble()
            val deltaStops = abs(log2(v.exact / implied))
            assertTrue("${v.nominal}: off by $deltaStops stops", deltaStops <= 0.17)
        }
        Stops.ISOS.forEach { v ->
            val deltaStops = abs(log2(v.exact / v.nominal.toDouble()))
            assertTrue("ISO ${v.nominal}: off by $deltaStops stops", deltaStops <= 0.17)
        }
    }

    @Test
    fun `key lattice anchors are exact`() {
        assertEquals(1.0, Stops.APERTURES.first { it.nominal == "1.0" }.exact, 1e-12)
        assertEquals(8.0, Stops.APERTURES.first { it.nominal == "8" }.exact, 1e-12)
        assertEquals(64.0, Stops.APERTURES.first { it.nominal == "64" }.exact, 1e-12)
        assertEquals(1.0, Stops.SHUTTERS.first { it.nominal == "1\"" }.exact, 1e-12)
        assertEquals(1.0 / 128.0, Stops.SHUTTERS.first { it.nominal == "1/125" }.exact, 1e-12)
        assertEquals(16.0, Stops.SHUTTERS.first { it.nominal == "15\"" }.exact, 1e-12)
        assertEquals(64.0, Stops.SHUTTERS.first { it.nominal == "60\"" }.exact, 1e-12)
        assertEquals(100.0, Stops.ISOS.first { it.nominal == "100" }.exact, 1e-9)
        assertEquals(400.0, Stops.ISOS.first { it.nominal == "400" }.exact, 1e-9)
    }

    @Test
    fun `aperture snapping picks nearest in log space`() {
        assertEquals("2", Stops.snapAperture(1.9).value.nominal)
        assertEquals("1.8", Stops.snapAperture(1.8).value.nominal)
        assertEquals("5.6", Stops.snapAperture(5.66).value.nominal)
    }

    @Test
    fun `shutter snapping picks nearest in log space`() {
        assertEquals("1/80", Stops.snapShutter(1.0 / 90.0).value.nominal)
        assertEquals("1/8000", Stops.snapShutter(1.0 / 8192.0).value.nominal)
        assertEquals("30\"", Stops.snapShutter(32.0).value.nominal)
    }

    @Test
    fun `snapping clamps out-of-range values and flags them`() {
        val tooFast = Stops.snapShutter(1.0 / 20_000.0)
        assertEquals("1/8000", tooFast.value.nominal)
        assertTrue(tooFast.outOfRange)

        val tooSlow = Stops.snapShutter(120.0)
        assertEquals("60\"", tooSlow.value.nominal)
        assertTrue(tooSlow.outOfRange)

        val tooWide = Stops.snapAperture(0.7)
        assertEquals("1.0", tooWide.value.nominal)
        assertTrue(tooWide.outOfRange)
    }

    @Test
    fun `iso snapping`() {
        assertEquals("400", Stops.snapIso(400.0).value.nominal)
        assertEquals("100", Stops.snapIso(105.0).value.nominal)
    }

    @Test
    fun `long exposure formatting`() {
        assertEquals("35s", Stops.formatSeconds(35.2))
        assertEquals("2.5s", Stops.formatSeconds(2.5))
        assertEquals("4s", Stops.formatSeconds(4.0))
        assertEquals("20m 00s", Stops.formatSeconds(1200.0))
        assertEquals("1/8", Stops.formatSeconds(0.125))
    }
}
