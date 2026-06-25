package com.uninsubria.derma_bsa

import com.uninsubria.derma_bsa.model.PasiRegion
import com.uninsubria.derma_bsa.model.regioniPerEta
import com.uninsubria.derma_bsa.util.BsaCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

/** Test della logica pura: `./gradlew test`. */
class BsaLogicTest {

    private val etaRappresentative = listOf(2, 7, 12, 20)

    @Test
    fun mappaSomma100() {
        for (eta in etaRappresentative) {
            val regioni = regioniPerEta(eta)
            assertEquals(
                "Somma BSA != 100 per età $eta",
                100f,
                regioni.sumOf { it.bsaPercent.toDouble() }.toFloat(),
                0.1f
            )
        }
    }

    @Test
    fun sommaMultiDistretto() {
        for (eta in etaRappresentative) {
            val regioni = regioniPerEta(eta)
            val misure = listOf(
                RegionMeasurement(regioni.first { it.id == "torso_back" }, 4.5f),
                RegionMeasurement(regioni.first { it.id == "upper_arm_right_front" }, 3.6f)
            )
            assertEquals("bsaTotale errato per età $eta", 8.1f, BsaCalculator.bsaTotale(misure), 0.001f)
        }
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
        // bustoTot per fascia: 1-4→32%, 5-9→32%, 10-14→32%, 15+→37%
        val attesi = mapOf(2 to 14.063f, 7 to 14.063f, 12 to 14.063f, 20 to 12.162f)
        for (eta in etaRappresentative) {
            val regioni = regioniPerEta(eta)
            val misure = listOf(
                RegionMeasurement(regioni.first { it.id == "torso_back" }, 4.5f)
            )
            val pasi = BsaCalculator.percentualiPasi(misure, regioni)
            assertEquals(
                "PASI tronco errato per età $eta",
                attesi[eta]!!,
                pasi[PasiRegion.TRONCO]!!,
                0.01f
            )
        }
    }
}
