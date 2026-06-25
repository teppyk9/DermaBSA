package com.uninsubria.derma_bsa.model

data class PatientConBsa(
    val id: Long,
    val nome: String,
    val cognome: String,
    val etaAnni: Long,
    val dataCreazione: Long,
    val totalBsa: Float
)
