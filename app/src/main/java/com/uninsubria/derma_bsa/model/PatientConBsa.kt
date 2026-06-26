package com.uninsubria.derma_bsa.model

/**
 * Proiezione usata dalla query Room per mostrare il paziente con il BSA totale
 * dell'ultima sessione di misurazione.
 *
 * @property id chiave primaria del paziente
 * @property nome nome del paziente
 * @property cognome cognome del paziente
 * @property etaAnni età rappresentativa della fascia d'età (2, 7, 12 o 20 anni)
 * @property dataCreazione timestamp di creazione del record in millisecondi
 * @property totalBsa somma dei contributi BSA dell'ultima sessione
 */
data class PatientConBsa(
    val id: Long,
    val nome: String,
    val cognome: String,
    val etaAnni: Long,
    val dataCreazione: Long,
    val totalBsa: Float
)
