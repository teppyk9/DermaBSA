package com.uninsubria.derma_bsa.model

import kotlinx.coroutines.flow.Flow

/**
 * Livello intermedio tra i DAO e il ViewModel.
 * Raggruppa le operazioni su pazienti, sessioni e misure in un unico punto.
 */
class PatientRepository(
    private val patientDao: PatientDao,
    private val sessionDao: SessionDao,
    private val measurementDao: MeasurementDao
) {

    /** Ritorna il flow dei pazienti con BSA aggregato. */
    fun getAllPazientiConBsa(): Flow<List<PatientConBsa>> = patientDao.getAllConBsa()

    /**
     * Crea un nuovo paziente nel database.
     * @return id del paziente inserito
     */
    suspend fun creaPaziente(nome: String, cognome: String, etaAnni: Long): Long =
        patientDao.inserisci(Patient(nome = nome, cognome = cognome, etaAnni = etaAnni))

    /**
     * Crea una nuova sessione per il paziente indicato.
     * @return id della sessione creata
     */
    suspend fun creaSessione(patientId: Long): Long =
        sessionDao.inserisci(Session(patientId = patientId))

    /** Ritorna il flow delle sessioni con le misure per un paziente. */
    fun getSessioniConMisurePerPaziente(patientId: Long): Flow<List<SessioneConMisure>> =
        sessionDao.getSessioniConMisurePerPaziente(patientId)

    /** Salva una misura, sostituendo l'eventuale record precedente per la stessa regione e sessione. */
    suspend fun salvaMisura(misura: Measurement) {
        measurementDao.eliminaPerRegioneNellaSessione(misura.sessionId, misura.regionId)
        measurementDao.inserisci(misura)
    }

    /**
     * Elimina la sessione e ritorna i percorsi dei file foto da cancellare dal filesystem.
     * @return lista di path da eliminare
     */
    suspend fun eliminaSessione(sessionId: Long): List<String> {
        val misure = measurementDao.getMisurePerSessioneOnce(sessionId)
        val percorsi = misure.flatMap { listOfNotNull(it.photoPath, it.maskPath) }
        sessionDao.elimina(sessionId)
        return percorsi
    }

    /**
     * Elimina il paziente e tutti i suoi dati, ritornando i percorsi dei file da cancellare.
     * @return lista di path da eliminare
     */
    suspend fun eliminaPaziente(patientId: Long): List<String> {
        val misure = measurementDao.getMisurePerPazienteOnce(patientId)
        val percorsi = misure.flatMap { listOfNotNull(it.photoPath, it.maskPath) }
        patientDao.elimina(patientId)
        return percorsi
    }
}
