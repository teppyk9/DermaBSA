package com.uninsubria.derma_bsa.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entità Room che rappresenta la misura BSA di un singolo distretto anatomico
 * all'interno di una sessione di visita.
 *
 * Più sessioni per lo stesso paziente e distretto vengono conservate tutte,
 * permettendo di tracciare l'evoluzione nel tempo. All'interno della stessa
 * sessione è possibile una sola misura per distretto.
 *
 * @property id chiave primaria generata automaticamente
 * @property patientId riferimento al paziente proprietario della misura
 * @property sessionId riferimento alla sessione di visita in cui è stata eseguita
 * @property regionId identificatore del distretto (es. "arm_left")
 * @property regionLabel nome leggibile del distretto (es. "Braccio sinistro")
 * @property bsaPercent contributo BSA calcolato per questo distretto
 * @property photoPath percorso su disco dell'immagine con maschera sovrapposta
 * @property maskPath percorso su disco della maschera di segmentazione
 * @property dataOra timestamp di salvataggio in millisecondi
 */
@Entity(
    tableName = "misure",
    foreignKeys = [
        ForeignKey(
            entity = Patient::class,
            parentColumns = ["id"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("patientId"), Index("sessionId")]
)
data class Measurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val sessionId: Long,
    val regionId: String,
    val regionLabel: String,
    val bsaPercent: Float,
    val photoPath: String?,
    val maskPath: String?,
    val dataOra: Long = System.currentTimeMillis()
)
