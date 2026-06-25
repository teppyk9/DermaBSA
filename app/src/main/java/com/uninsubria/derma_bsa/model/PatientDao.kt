package com.uninsubria.derma_bsa.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {

    @Query("""
        SELECT p.id, p.nome, p.cognome, p.dataNascita, p.dataCreazione,
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

    @Insert
    suspend fun inserisci(patient: Patient): Long

    @Query("SELECT * FROM pazienti WHERE id = :id")
    suspend fun getById(id: Long): Patient?

    @Query("DELETE FROM pazienti WHERE id = :id")
    suspend fun elimina(id: Long)
}
