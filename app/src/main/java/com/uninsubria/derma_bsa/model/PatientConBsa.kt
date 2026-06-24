package com.uninsubria.derma_bsa.model

/**
 * Risultato della query che unisce i dati del paziente con il totale BSA
 * calcolato come somma delle misure per distretto.
 *
 * Usato esclusivamente nella lista pazienti per mostrare il totale senza
 * caricare l'intera lista delle misure.
 *
 * @property id chiave primaria del paziente
 * @property nome nome del paziente
 * @property cognome cognome del paziente
 * @property dataCreazione timestamp di creazione del record
 * @property totalBsa somma dei contributi BSA di tutti i distretti misurati
 */
data class PatientConBsa(
    val id: Long,
    val nome: String,
    val cognome: String,
    val dataCreazione: Long,
    val totalBsa: Float
)
