package com.uninsubria.derma_bsa.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entità Room che rappresenta una sessione di misurazione per un paziente.
 *
 * Ogni click su "Aggiungi misurazione" crea una nuova sessione. Le misure
 * eseguite durante la stessa visita appartengono tutte a questa sessione,
 * consentendo di conservare lo storico completo per paziente.
 *
 * @property id chiave primaria generata automaticamente
 * @property patientId riferimento al paziente proprietario della sessione
 * @property dataOra timestamp di creazione della sessione in millisecondi
 */
@Entity(
    tableName = "sessioni",
    foreignKeys = [ForeignKey(
        entity = Patient::class,
        parentColumns = ["id"],
        childColumns = ["patientId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("patientId")]
)
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val dataOra: Long = System.currentTimeMillis()
)
