package com.uninsubria.derma_bsa.model

/**
 * Macro-regioni anatomiche usate dal sistema PASI, ognuna con il proprio peso.
 *
 * I pesi indicano la percentuale di superficie corporea totale che la regione
 * rappresenta: testa 10%, arti superiori 20%, tronco 30%, arti inferiori 40%.
 *
 * @property label nome leggibile della regione
 * @property peso frazione di superficie corporea (0.0–1.0)
 */
enum class PasiRegion(val label: String, val peso: Float) {
    TESTA("Testa", 0.1f),
    ARTI_SUPERIORI("Arti superiori", 0.2f),
    TRONCO("Tronco", 0.3f),
    ARTI_INFERIORI("Arti inferiori", 0.4f)
}

/**
 * Distretto anatomico secondo la Regola dei Nove.
 *
 * @property id identificatore univoco usato internamente (es. "arm_left")
 * @property label nome mostrato all'utente (es. "Braccio sinistro")
 * @property bsaPercent percentuale di superficie corporea totale che questo distretto rappresenta
 * @property pasiRegion macro-regione PASI a cui appartiene il distretto
 */
data class BodyRegion(
    val id: String,
    val label: String,
    val bsaPercent: Float,
    val pasiRegion: PasiRegion
)

/**
 * Lista completa dei distretti anatomici supportati dall'applicazione.
 *
 * Le percentuali seguono la Regola dei Nove: la somma di tutti i distretti
 * è pari al 100% della superficie corporea totale.
 */
val ALL_REGIONS = listOf(
    BodyRegion("head",        "Testa",             9f,  PasiRegion.TESTA),
    BodyRegion("arm_right",   "Braccio destro",    9f,  PasiRegion.ARTI_SUPERIORI),
    BodyRegion("arm_left",    "Braccio sinistro",  9f,  PasiRegion.ARTI_SUPERIORI),
    BodyRegion("trunk_front", "Tronco anteriore",  18f, PasiRegion.TRONCO),
    BodyRegion("trunk_back",  "Tronco posteriore", 18f, PasiRegion.TRONCO),
    BodyRegion("leg_right",   "Gamba destra",      18f, PasiRegion.ARTI_INFERIORI),
    BodyRegion("leg_left",    "Gamba sinistra",    18f, PasiRegion.ARTI_INFERIORI),
    BodyRegion("genitals",    "Genitali",          1f,  PasiRegion.TRONCO)
)

/** Somma delle percentuali BSA di tutti i distretti; dovrebbe essere 100. */
val TOTALE_BSA: Float get() = ALL_REGIONS.sumOf { it.bsaPercent.toDouble() }.toFloat()
