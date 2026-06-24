package com.uninsubria.derma_bsa

import com.uninsubria.derma_bsa.model.ALL_REGIONS
import com.uninsubria.derma_bsa.model.PasiRegion
import com.uninsubria.derma_bsa.model.TOTALE_BSA
import com.uninsubria.derma_bsa.util.BsaCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

/** Test della logica pura: `./gradlew test`. */
class BsaLogicTest {

    private fun reg(id: String) = ALL_REGIONS.first { it.id == id }

    @Test
    fun mappaSomma100() {
        assertEquals(100f, TOTALE_BSA, 0.001f)
    }

    @Test
    fun sommaMultiDistretto() {
        val misure = listOf(
            RegionMeasurement(reg("trunk_back"), 4.5f),  // 18% * 0.25
            RegionMeasurement(reg("arm_right"), 3.6f)    // 9%  * 0.40
        )
        assertEquals(8.1f, BsaCalculator.bsaTotale(misure), 0.001f)
    }

    @Test
    fun bandeAreaScorePasi() {
        assertEquals(0, BsaCalculator.pasiAreaScore(0f))
        assertEquals(1, BsaCalculator.pasiAreaScore(5f))
        assertEquals(3, BsaCalculator.pasiAreaScore(40f))
        assertEquals(6, BsaCalculator.pasiAreaScore(95f))
    }

    @Test
    fun percentualePasiTronco() {
        // tronco coinvolto: 4.5 su (18+18+1)=37 -> ~12.16%
        val misure = listOf(RegionMeasurement(reg("trunk_back"), 4.5f))
        val pasi = BsaCalculator.percentualiPasi(misure)
        assertEquals(12.162f, pasi[PasiRegion.TRONCO]!!, 0.01f)
    }
}
