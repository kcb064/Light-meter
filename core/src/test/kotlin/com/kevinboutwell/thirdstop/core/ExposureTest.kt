package com.kevinboutwell.thirdstop.core

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.pow

class ExposureTest {

    @Test
    fun `sunny 16 exposure reads about EV 15`() {
        // f/16 at 1/125 (nominal), ISO 100 — the sunny-16 rule.
        val ev = Exposure.ev100FromExposure(16.0, 1.0 / 125.0, 100.0)
        assertEquals(15.0, ev, 0.05)
    }

    @Test
    fun `EV 15 at ISO 100 and f16 solves to exactly the third-stop 1_125`() {
        val t = Exposure.shutterFor(Exposure.evAtIso(15.0, 100.0), 16.0)
        assertEquals(1.0 / 128.0, t, 1e-12)
        val snapped = Stops.snapShutter(t)
        assertEquals("1/125", snapped.value.nominal)
        assertEquals(false, snapped.outOfRange)
    }

    @Test
    fun `lux to EV100 anchors`() {
        assertEquals(0.0, Exposure.ev100FromLux(2.5), 1e-12)
        assertEquals(15.0, Exposure.ev100FromLux(2.5 * 2.0.pow(15)), 1e-12)
        // Bright direct sun is ~100k lux — a touch over EV 15.
        assertEquals(15.29, Exposure.ev100FromLux(100_000.0), 0.01)
    }

    @Test
    fun `lux round-trips through EV100`() {
        val lux = 12_345.0
        assertEquals(lux, Exposure.luxFromEv100(Exposure.ev100FromLux(lux)), 1e-6)
    }

    @Test
    fun `ISO transposition - EV 15 at ISO 400 pairs f16 with 1_500`() {
        val evIso = Exposure.evAtIso(15.0, 400.0)
        assertEquals(17.0, evIso, 1e-12)
        val snapped = Stops.snapShutter(Exposure.shutterFor(evIso, 16.0))
        assertEquals("1/500", snapped.value.nominal)
    }

    @Test
    fun `nanosecond exposure time converts and snaps to 1_60`() {
        val seconds = Exposure.exposureTimeNsToSeconds(16_666_667L)
        assertEquals(1.0 / 60.0, seconds, 1e-9)
        assertEquals("1/60", Stops.snapShutter(seconds).value.nominal)
    }

    @Test
    fun `camera AE example - typical indoor reading`() {
        // ISO 800, 1/30 s, f/1.8 -> EV100 = log2(1.8^2 * 30) - 3 = ~3.6
        val ev = Exposure.ev100FromExposure(1.8, 1.0 / 30.0, 800.0)
        assertEquals(3.60, ev, 0.01)
    }
}
