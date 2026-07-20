package com.kevinboutwell.thirdstop.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ReciprocityTest {

    @Test
    fun `power model matches Ilford formula`() {
        val hp5 = ReciprocityModel.Power(1.31)
        assertEquals(10.0.pow(1.31), hp5.correct(10.0), 1e-9) // ~20.4s
        assertEquals(20.4, hp5.correct(10.0), 0.1)
    }

    @Test
    fun `power model is identity at and below threshold`() {
        val hp5 = ReciprocityModel.Power(1.31)
        assertEquals(0.5, hp5.correct(0.5), 1e-12)
        assertEquals(1.0, hp5.correct(1.0), 1e-12)
        assertFalse(hp5.applies(0.5))
        assertTrue(hp5.applies(10.0))
    }

    @Test
    fun `schedule hits datasheet anchors exactly`() {
        val trix = FilmStocks.byId("kodak-trix").reciprocity
        assertEquals(2.0, trix.correct(1.0), 1e-9)
        assertEquals(50.0, trix.correct(10.0), 1e-9)
        assertEquals(1200.0, trix.correct(100.0), 1e-9)
    }

    @Test
    fun `schedule is identity below first anchor`() {
        val trix = FilmStocks.byId("kodak-trix").reciprocity
        assertEquals(0.05, trix.correct(0.05), 1e-12)
        assertFalse(trix.applies(0.05))
    }

    @Test
    fun `schedule interpolates monotonically between anchors`() {
        val trix = FilmStocks.byId("kodak-trix").reciprocity
        var prev = 0.0
        for (t in listOf(0.5, 1.0, 2.0, 5.0, 10.0, 30.0, 100.0)) {
            val c = trix.correct(t)
            assertTrue("corrected($t)=$c should exceed metered", c >= t)
            assertTrue("corrected($t)=$c should be monotonic", c > prev)
            prev = c
        }
        // Between the 1s->2s and 10s->50s anchors.
        val mid = trix.correct(5.0)
        assertTrue("$mid", mid > 5.0 && mid < 50.0)
    }

    @Test
    fun `schedule extrapolates beyond the last anchor`() {
        val trix = FilmStocks.byId("kodak-trix").reciprocity
        val beyond = trix.correct(200.0)
        assertTrue("$beyond", beyond > 1200.0)
    }

    @Test
    fun `none model never corrects`() {
        val provia = FilmStocks.byId("fuji-provia100f").reciprocity
        assertEquals(60.0, provia.correct(60.0), 1e-12)
        assertFalse(provia.applies(120.0))
    }

    @Test
    fun `velvia 100 needs nothing below a minute`() {
        val velvia = FilmStocks.byId("fuji-velvia100").reciprocity
        assertFalse(velvia.applies(30.0))
        assertTrue(velvia.applies(120.0))
    }

    @Test
    fun `all film stocks have unique ids and sane models`() {
        val ids = FilmStocks.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        FilmStocks.ALL.forEach { film ->
            // Correction must never *reduce* exposure anywhere in the practical range.
            for (t in listOf(0.01, 0.1, 1.0, 5.0, 30.0, 120.0)) {
                assertTrue(
                    "${film.id} at ${t}s",
                    film.reciprocity.correct(t) >= t - 1e-9,
                )
            }
        }
    }
}
