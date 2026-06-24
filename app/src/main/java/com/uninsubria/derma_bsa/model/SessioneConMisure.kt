package com.uninsubria.derma_bsa.model

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Risultato della query Room che unisce una sessione con le misure eseguite
 * durante quella sessione, usando la relazione uno-a-molti tramite [Relation].
 *
 * Usato nel dettaglio paziente per mostrare lo storico completo suddiviso
 * per sessione di visita.
 *
 * @property sessione sessione di misurazione
 * @property misure lista delle misure per distretto registrate nella sessione
 */
data class SessioneConMisure(
    @Embedded val sessione: Session,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val misure: List<Measurement>
)
