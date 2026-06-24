package com.uninsubria.derma_bsa.model

import kotlinx.coroutines.flow.Flow

/**
 * Repository che centralizza l'accesso ai dati di pazienti, sessioni e misure.
 *
 * Fa da intermediario tra il ViewModel e i DAO Room, esponendo solo
 * le operazioni necessarie all'interfaccia utente.
 *
 * @property patientDao DAO per le operazioni sui pazienti
 * @property sessionDao DAO per le operazioni sulle sessioni
 * @property measurementDao DAO per le operazioni sulle misure
 */
class PatientRepository(
    private val patientDao: PatientDao,
    private val sessionDao: SessionDao,
    private val measurementDao: MeasurementDao
) {

    /**
     * Restituisce un Flow con la lista aggiornata di tutti i pazienti e
     * il BSA totale dell'ultima sessione.
     */
    fun getAllPazientiConBsa(): Flow<List<PatientConBsa>> = patientDao.getAllConBsa()

    /**
     * Crea un nuovo paziente nel database e restituisce l'id assegnato.
     *
     * @param nome nome del paziente
     * @param cognome cognome del paziente
     * @return id del paziente appena creato
     */
    suspend fun creaPaziente(nome: String, cognome: String): Long =
        patientDao.inserisci(Patient(nome = nome, cognome = cognome))

    /**
     * Crea una nuova sessione di visita per il paziente e restituisce l'id assegnato.
     *
     * @param patientId id del paziente
     * @return id della sessione appena creata
     */
    suspend fun creaSessione(patientId: Long): Long =
        sessionDao.inserisci(Session(patientId = patientId))

    /**
     * Restituisce le sessioni di un paziente con le relative misure come Flow reattivo,
     * ordinate dalla più recente alla più vecchia.
     *
     * @param patientId id del paziente
     */
    fun getSessioniConMisurePerPaziente(patientId: Long): Flow<List<SessioneConMisure>> =
        sessionDao.getSessioniConMisurePerPaziente(patientId)

    /**
     * Salva una misura eliminando prima l'eventuale misura precedente per lo stesso
     * distretto all'interno della stessa sessione.
     * Sessioni diverse conservano le proprie misure indipendentemente.
     *
     * @param misura misura da salvare
     */
    suspend fun salvaMisura(misura: Measurement) {
        measurementDao.eliminaPerRegioneNellaSessione(misura.sessionId, misura.regionId)
        measurementDao.inserisci(misura)
    }

    /**
     * Elimina una sessione e tutte le sue misure dal database.
     * Restituisce i percorsi dei file da cancellare su disco (overlay e maschere).
     *
     * @param sessionId id della sessione da eliminare
     * @return lista di percorsi file da cancellare
     */
    suspend fun eliminaSessione(sessionId: Long): List<String> {
        val misure = measurementDao.getMisurePerSessioneOnce(sessionId)
        val percorsi = misure.flatMap { listOfNotNull(it.photoPath, it.maskPath) }
        sessionDao.elimina(sessionId)
        return percorsi
    }

    /**
     * Elimina un paziente e tutti i suoi dati (sessioni e misure) dal database.
     * Restituisce i percorsi dei file da cancellare su disco.
     *
     * @param patientId id del paziente da eliminare
     * @return lista di percorsi file da cancellare
     */
    suspend fun eliminaPaziente(patientId: Long): List<String> {
        val misure = measurementDao.getMisurePerPazienteOnce(patientId)
        val percorsi = misure.flatMap { listOfNotNull(it.photoPath, it.maskPath) }
        patientDao.elimina(patientId)
        return percorsi
    }
}
