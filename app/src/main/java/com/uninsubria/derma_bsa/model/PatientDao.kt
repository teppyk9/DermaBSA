package com.uninsubria.derma_bsa.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO per le operazioni sul database relative ai pazienti.
 */
@Dao
interface PatientDao {

    /**
     * Restituisce tutti i pazienti con il BSA totale dell'ultima sessione,
     * in ordine di creazione decrescente.
     *
     * Il BSA mostrato è quello dell'ultima sessione registrata: se il paziente
     * non ha ancora sessioni, totalBsa vale 0.
     *
     * @return Flow aggiornato automaticamente ad ogni modifica del database
     */
    @Query("""
        SELECT p.id, p.nome, p.cognome, p.dataCreazione,
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

    /**
     * Inserisce un nuovo paziente e restituisce l'id generato.
     *
     * @param patient paziente da inserire
     * @return id assegnato dal database
     */
    @Insert
    suspend fun inserisci(patient: Patient): Long

    /**
     * Restituisce il paziente con l'id specificato, o null se non esiste.
     *
     * @param id chiave primaria del paziente
     */
    @Query("SELECT * FROM pazienti WHERE id = :id")
    suspend fun getById(id: Long): Patient?

    /**
     * Elimina il paziente con l'id specificato.
     * Sessioni e misure collegate vengono eliminate automaticamente per CASCADE.
     *
     * @param id chiave primaria del paziente da eliminare
     */
    @Query("DELETE FROM pazienti WHERE id = :id")
    suspend fun elimina(id: Long)
}
