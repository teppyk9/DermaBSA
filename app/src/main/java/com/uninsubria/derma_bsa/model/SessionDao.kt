package com.uninsubria.derma_bsa.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * DAO per le operazioni sul database relative alle sessioni di misurazione.
 */
@Dao
interface SessionDao {

    /**
     * Inserisce una nuova sessione e restituisce l'id generato.
     *
     * @param session sessione da inserire
     * @return id assegnato dal database
     */
    @Insert
    suspend fun inserisci(session: Session): Long

    /**
     * Elimina la sessione con l'id specificato.
     * Le misure collegate vengono eliminate automaticamente per CASCADE.
     *
     * @param sessionId id della sessione da eliminare
     */
    @Query("DELETE FROM sessioni WHERE id = :sessionId")
    suspend fun elimina(sessionId: Long)

    /**
     * Restituisce tutte le sessioni di un paziente con le relative misure,
     * ordinate dalla più recente alla più vecchia.
     *
     * @param patientId id del paziente
     * @return Flow aggiornato automaticamente ad ogni modifica
     */
    @Transaction
    @Query("SELECT * FROM sessioni WHERE patientId = :patientId ORDER BY dataOra DESC")
    fun getSessioniConMisurePerPaziente(patientId: Long): Flow<List<SessioneConMisure>>
}
