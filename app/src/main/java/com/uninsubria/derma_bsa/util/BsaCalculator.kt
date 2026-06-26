package com.uninsubria.derma_bsa.util

import com.uninsubria.derma_bsa.RegionMeasurement
import com.uninsubria.derma_bsa.model.BodyRegion
import com.uninsubria.derma_bsa.model.PasiRegion

/**
 * Funzioni di calcolo BSA e PASI per la sessione corrente.
 * Non ha dipendenze Android, quindi è testabile direttamente con JUnit.
 */
object BsaCalculator {

    /**
     * Somma i contributi BSA di tutti i distretti misurati, limitato a 100.
     *
     * @param misure misure accumulate nella sessione
     * @return BSA totale in percentuale
     */
    fun bsaTotale(misure: List<RegionMeasurement>): Float =
        misure.sumOf { it.bsaPercent.toDouble() }.toFloat().coerceAtMost(100f)

    /**
     * Per ogni macro-regione PASI calcola la percentuale della regione coinvolta,
     * rapportando il BSA misurato alla superficie totale di quella regione.
     *
     * @param misure misure accumulate nella sessione
     * @param tuteLeRegioni distretti calibrati per l'età del paziente corrente
     * @return mappa PASI → percentuale della regione coinvolta
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

    /**
     * Converte la percentuale di una macro-regione PASI nell'area score corrispondente.
     *
     * @param percentualeRegione percentuale della regione coinvolta
     * @return area score PASI (0–6)
     */
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
