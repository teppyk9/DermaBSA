package com.uninsubria.derma_bsa.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO per le operazioni sul database relative alle misure BSA per distretto.
 */
@Dao
interface MeasurementDao {

    /**
     * Restituisce tutte le misure del paziente specificato, ordinate per data.
     *
     * @param patientId id del paziente
     * @return Flow aggiornato automaticamente ad ogni modifica
     */
    @Query("SELECT * FROM misure WHERE patientId = :patientId ORDER BY dataOra ASC")
    fun getMisurePerPaziente(patientId: Long): Flow<List<Measurement>>

    /**
     * Elimina la misura precedente per lo stesso distretto all'interno della stessa
     * sessione, in modo da mantenere al massimo una misura per distretto per sessione.
     * Sessioni diverse possono invece avere misure per lo stesso distretto.
     *
     * @param sessionId id della sessione corrente
     * @param regionId identificatore del distretto
     */
    @Query("DELETE FROM misure WHERE sessionId = :sessionId AND regionId = :regionId")
    suspend fun eliminaPerRegioneNellaSessione(sessionId: Long, regionId: String)

    /**
     * Restituisce una volta sola (non reattivo) tutte le misure di una sessione.
     * Usato prima di eliminare la sessione per cancellare i file su disco.
     *
     * @param sessionId id della sessione
     */
    @Query("SELECT * FROM misure WHERE sessionId = :sessionId")
    suspend fun getMisurePerSessioneOnce(sessionId: Long): List<Measurement>

    /**
     * Restituisce una volta sola (non reattivo) tutte le misure di un paziente.
     * Usato prima di eliminare il paziente per cancellare i file su disco.
     *
     * @param patientId id del paziente
     */
    @Query("SELECT * FROM misure WHERE patientId = :patientId")
    suspend fun getMisurePerPazienteOnce(patientId: Long): List<Measurement>

    /**
     * Inserisce una nuova misura nel database.
     *
     * @param measurement misura da inserire
     */
    @Insert
    suspend fun inserisci(measurement: Measurement)
}
