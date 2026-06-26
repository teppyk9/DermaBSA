package com.uninsubria.derma_bsa.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** DAO per le operazioni sui pazienti e la query con BSA aggregato. */
@Dao
interface PatientDao {

    /** Ritorna i pazienti con il BSA totale dell'ultima sessione, aggiornato in tempo reale. */
    @Query("""
        SELECT p.id, p.nome, p.cognome, p.etaAnni, p.dataCreazione,
               COALESCE((
                   SELECT SUM(m.bsaPercent)
                   FROM misure m
                   WHERE m.sessionId = (
                       SELECT s.id FROM sessioni s
                       WHERE s.patientId = p.id
                       ORDER BY s.dataOra DESC LIMIT 1
                   )
               ), 0.0) AS totalBsa
        FROM pazienti p
        ORDER BY p.dataCreazione DESC
    """)
    fun getAllConBsa(): Flow<List<PatientConBsa>>

    /** Inserisce un paziente e ritorna il suo id generato. */
    @Insert
    suspend fun inserisci(patient: Patient): Long

    /** Ritorna il paziente con l'id indicato, o null se non esiste. */
    @Query("SELECT * FROM pazienti WHERE id = :id")
    suspend fun getById(id: Long): Patient?

    /** Elimina il paziente; le sessioni e misure collegate vengono rimosse a cascata. */
    @Query("DELETE FROM pazienti WHERE id = :id")
    suspend fun elimina(id: Long)
}
