package com.uninsubria.derma_bsa.model

import kotlinx.coroutines.flow.Flow

class PatientRepository(
    private val patientDao: PatientDao,
    private val sessionDao: SessionDao,
    private val measurementDao: MeasurementDao
) {

    fun getAllPazientiConBsa(): Flow<List<PatientConBsa>> = patientDao.getAllConBsa()

    suspend fun creaPaziente(nome: String, cognome: String, dataNascita: Long): Long =
        patientDao.inserisci(Patient(nome = nome, cognome = cognome, dataNascita = dataNascita))

    suspend fun creaSessione(patientId: Long): Long =
        sessionDao.inserisci(Session(patientId = patientId))

    fun getSessioniConMisurePerPaziente(patientId: Long): Flow<List<SessioneConMisure>> =
        sessionDao.getSessioniConMisurePerPaziente(patientId)

    suspend fun salvaMisura(misura: Measurement) {
        measurementDao.eliminaPerRegioneNellaSessione(misura.sessionId, misura.regionId)
        measurementDao.inserisci(misura)
    }

    suspend fun eliminaSessione(sessionId: Long): List<String> {
        val misure = measurementDao.getMisurePerSessioneOnce(sessionId)
        val percorsi = misure.flatMap { listOfNotNull(it.photoPath, it.maskPath) }
        sessionDao.elimina(sessionId)
        return percorsi
    }

    suspend fun eliminaPaziente(patientId: Long): List<String> {
        val misure = measurementDao.getMisurePerPazienteOnce(patientId)
        val percorsi = misure.flatMap { listOfNotNull(it.photoPath, it.maskPath) }
        patientDao.elimina(patientId)
        return percorsi
    }
}
