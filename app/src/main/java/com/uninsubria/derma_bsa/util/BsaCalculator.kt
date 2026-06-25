package com.uninsubria.derma_bsa.util

import com.uninsubria.derma_bsa.RegionMeasurement
import com.uninsubria.derma_bsa.model.BodyRegion
import com.uninsubria.derma_bsa.model.PasiRegion

object BsaCalculator {

    fun bsaTotale(misure: List<RegionMeasurement>): Float =
        misure.sumOf { it.bsaPercent.toDouble() }.toFloat().coerceAtMost(100f)

    /**
     * Calcola la percentuale di regione PASI coinvolta per ogni macro-area.
     *
     * @param misure misure della sessione corrente
     * @param tuteLeRegioni lista completa dei distretti calibrata per l'età del paziente
     */
    fun percentualiPasi(
        misure: List<RegionMeasurement>,
        tuteLeRegioni: List<BodyRegion>
    ): Map<PasiRegion, Float> {
        return misure.groupBy { it.region.pasiRegion }.mapValues { (regione, ms) ->
            val areaTotaleRegione = tuteLeRegioni
                .filter { it.pasiRegion == regione }
                .sumOf { it.bsaPercent.toDouble() }
                .toFloat()
            if (areaTotaleRegione <= 0f) 0f
            else (ms.sumOf { it.bsaPercent.toDouble() }.toFloat() / areaTotaleRegione) * 100f
        }
    }

    fun pasiAreaScore(percentualeRegione: Float): Int = when {
        percentualeRegione <= 0f -> 0
        percentualeRegione < 10f -> 1
        percentualeRegione < 30f -> 2
        percentualeRegione < 50f -> 3
        percentualeRegione < 70f -> 4
        percentualeRegione < 90f -> 5
        else                     -> 6
    }
}
