package com.kevinboutwell.thirdstop.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExposureSolverTest {

    private val iso100 = Stops.ISO_100
    private fun aperture(nominal: String) = Stops.APERTURES.first { it.nominal == nominal }
    private fun shutter(nominal: String) = Stops.SHUTTERS.first { it.nominal == nominal }

    @Test
    fun `aperture priority - sunny 16`() {
        val s = ExposureSolver.solve(15.0, iso100, 0.0, Priority.APERTURE, aperture("16"))
        assertEquals("1/125", s.shutter.nominal)
        assertEquals("16", s.aperture.nominal)
        assertFalse(s.outOfRange)
        assertNull(s.correctedSeconds)
    }

    @Test
    fun `shutter priority - sunny 16 inverse`() {
        val s = ExposureSolver.solve(15.0, iso100, 0.0, Priority.SHUTTER, shutter("1/125"))
        assertEquals("16", s.aperture.nominal)
        assertEquals("1/125", s.shutter.nominal)
    }

    @Test
    fun `positive EC gives more exposure - plus one stop doubles shutter time`() {
        val base = ExposureSolver.solve(12.0, iso100, 0.0, Priority.APERTURE, aperture("8"))
        val comped = ExposureSolver.solve(12.0, iso100, 1.0, Priority.APERTURE, aperture("8"))
        assertEquals(2.0, comped.shutter.exact / base.shutter.exact, 1e-9)
    }

    @Test
    fun `third-stop EC moves shutter exactly one lattice step`() {
        val base = ExposureSolver.solve(12.0, iso100, 0.0, Priority.APERTURE, aperture("8"))
        val comped = ExposureSolver.solve(12.0, iso100, 1.0 / 3.0, Priority.APERTURE, aperture("8"))
        assertEquals(2.0, (comped.shutter.exact / base.shutter.exact).let { it * it * it }, 1e-6)
    }

    @Test
    fun `higher ISO shortens shutter`() {
        val iso400 = Stops.ISOS.first { it.nominal == "400" }
        val at100 = ExposureSolver.solve(10.0, iso100, 0.0, Priority.APERTURE, aperture("5.6"))
        val at400 = ExposureSolver.solve(10.0, iso400, 0.0, Priority.APERTURE, aperture("5.6"))
        assertEquals(0.25, at400.shutter.exact / at100.shutter.exact, 1e-9)
    }

    @Test
    fun `dim scene with fast shutter runs out of aperture range`() {
        val s = ExposureSolver.solve(2.0, iso100, 0.0, Priority.SHUTTER, shutter("1/1000"))
        assertTrue(s.outOfRange)
        assertEquals("1.0", s.aperture.nominal)
    }

    @Test
    fun `bright scene at small aperture runs out of shutter range`() {
        val s = ExposureSolver.solve(20.0, iso100, 0.0, Priority.APERTURE, aperture("1.0"))
        assertTrue(s.outOfRange)
        assertEquals("1/8000", s.shutter.nominal)
    }

    @Test
    fun `reciprocity correction appears only for long exposures`() {
        val hp5 = FilmStocks.byId("ilford-hp5")
        // EV 3 at ISO 100 (using HP5 as if rated 100 for the test), f/16 -> 32s metered.
        val slow = ExposureSolver.solve(3.0, iso100, 0.0, Priority.APERTURE, aperture("16"), hp5)
        assertEquals(32.0, slow.meteredSeconds, 1e-9)
        assertNotNull(slow.correctedSeconds)
        assertTrue(slow.correctedSeconds!! > slow.meteredSeconds)

        val fast = ExposureSolver.solve(15.0, iso100, 0.0, Priority.APERTURE, aperture("16"), hp5)
        assertNull(fast.correctedSeconds)
    }

    @Test
    fun `no film means no correction even for long exposures`() {
        val s = ExposureSolver.solve(3.0, iso100, 0.0, Priority.APERTURE, aperture("16"), FilmStocks.NONE)
        assertNull(s.correctedSeconds)
    }
}
