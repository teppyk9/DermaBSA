package com.uninsubria.derma_bsa.util

import com.uninsubria.derma_bsa.RegionMeasurement
import com.uninsubria.derma_bsa.model.ALL_REGIONS
import com.uninsubria.derma_bsa.model.PasiRegion

/**
 * Raccoglie le funzioni di calcolo BSA e PASI.
 *
 * Tutte le funzioni sono pure (nessun effetto collaterale, nessuna dipendenza Android)
 * e quindi testabili direttamente con JUnit senza bisogno di un dispositivo.
 */
object BsaCalculator {

    /**
     * Calcola il BSA totale coinvolto sommando i contributi di tutti i distretti misurati.
     * Il risultato è limitato a 100% per evitare valori impossibili.
     *
     * @param misure lista delle misure accumulate nella sessione
     * @return percentuale di superficie corporea totale coinvolta (0–100)
     */
    fun bsaTotale(misure: List<RegionMeasurement>): Float =
        misure.sumOf { it.bsaPercent.toDouble() }.toFloat().coerceAtMost(100f)

    /**
     * Calcola, per ogni macro-regione PASI, la percentuale di superficie
     * della regione che risulta coinvolta.
     *
     * La formula è: somma dei contributi dei distretti misurati nella regione
     * diviso la superficie totale di quella regione, moltiplicato per 100.
     * Questo valore è quello corretto da passare a [pasiAreaScore].
     *
     * @param misure lista delle misure accumulate nella sessione
     * @return mappa da macro-regione PASI alla percentuale di regione coinvolta (0–100)
     */
    fun percentualiPasi(misure: List<RegionMeasurement>): Map<PasiRegion, Float> {
        return misure.groupBy { it.region.pasiRegion }.mapValues { (regione, ms) ->
            val areaTotaleRegione = ALL_REGIONS
                .filter { it.pasiRegion == regione }
                .sumOf { it.bsaPercent.toDouble() }
                .toFloat()
            if (areaTotaleRegione <= 0f) 0f
            else (ms.sumOf { it.bsaPercent.toDouble() }.toFloat() / areaTotaleRegione) * 100f
        }
    }

    /**
     * Converte la percentuale di una macro-regione PASI coinvolta nel corrispondente
     * area score secondo le bande standard del sistema PASI (0–6).
     *
     * @param percentualeRegione percentuale di regione coinvolta (0–100)
     * @return area score PASI da 0 (nulla) a 6 (totale)
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
