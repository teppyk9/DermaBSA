package com.uninsubria.derma_bsa.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entità Room che rappresenta un paziente nella sessione di misurazione.
 *
 * @property id chiave primaria generata automaticamente dal database
 * @property nome nome del paziente
 * @property cognome cognome del paziente
 * @property dataCreazione timestamp di creazione del record in millisecondi
 */
@Entity(tableName = "pazienti")
data class Patient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val cognome: String,
    val dataCreazione: Long = System.currentTimeMillis()
)
