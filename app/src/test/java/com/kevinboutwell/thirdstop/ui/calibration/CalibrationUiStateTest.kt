package com.kevinboutwell.thirdstop.ui.calibration

import com.kevinboutwell.thirdstop.data.CalibrationSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalibrationUiStateTest {

    private fun withEvText(text: String) = CalibrationUiState(
        source = CalibrationSource.REFERENCE_METER,
        entryMode = ReferenceEntry.EV_DIRECT,
        evText = text,
    )

    @Test
    fun `sane EV text parses`() {
        assertEquals(15.0, withEvText("15").trustedEv100!!, 1e-9)
        assertEquals(-6.5, withEvText(" -6.5 ").trustedEv100!!, 1e-9)
        assertEquals(25.0, withEvText("25").trustedEv100!!, 1e-9)
    }

    @Test
    fun `absurd or non-finite EV text is rejected`() {
        // These once flowed into a persisted calibration offset that crashed
        // the meter screen on every subsequent launch.
        listOf("1e999", "NaN", "Infinity", "-Infinity", "9999", "-9999", "abc", "").forEach {
            assertNull("'$it' should be rejected", withEvText(it).trustedEv100)
        }
    }

    @Test
    fun `sunny 16 ignores the text field`() {
        val state = withEvText("garbage").copy(source = CalibrationSource.SUNNY_16)
        assertEquals(CalibrationUiState.SUNNY_16_EV100, state.trustedEv100!!, 1e-9)
    }
}
