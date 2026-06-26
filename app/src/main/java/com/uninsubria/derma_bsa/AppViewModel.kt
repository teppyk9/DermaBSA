package com.uninsubria.derma_bsa

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import com.uninsubria.derma_bsa.model.AppDatabase
import com.uninsubria.derma_bsa.model.BodyRegion
import com.uninsubria.derma_bsa.model.Measurement
import com.uninsubria.derma_bsa.model.PatientConBsa
import com.uninsubria.derma_bsa.model.PatientRepository
import com.uninsubria.derma_bsa.model.SessioneConMisure
import com.uninsubria.derma_bsa.model.regioniPerEta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Misura BSA per un singolo distretto anatomico accumulata in sessione.
 *
 * @property region distretto corporeo misurato
 * @property bsaPercent contributo percentuale al BSA totale del paziente
 */
data class RegionMeasurement(val region: BodyRegion, val bsaPercent: Float)

/**
 * ViewModel condiviso tra i Fragment, tiene lo stato della sessione corrente
 * e gestisce la persistenza su database tramite [PatientRepository].
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PatientRepository

    init {
        val db = AppDatabase.getInstance(application)
        repository = PatientRepository(db.patientDao(), db.sessionDao(), db.measurementDao())
    }

    var currentPatientId: Long = -1L
        private set

    var currentSessionId: Long = -1L
        private set

    var currentPatientEta: Long = 0L
        private set

    private val _selectedRegion = MutableStateFlow<BodyRegion?>(null)
    val selectedRegion: StateFlow<BodyRegion?> = _selectedRegion

    private val _measurements = MutableStateFlow<List<RegionMeasurement>>(emptyList())
    val measurements: StateFlow<List<RegionMeasurement>> = _measurements

    private val _totalBsa = MutableStateFlow(0f)
    val totalBsa: StateFlow<Float> = _totalBsa

    private val _croppedBitmap = MutableStateFlow<Bitmap?>(null)
    val croppedBitmap: StateFlow<Bitmap?> = _croppedBitmap

    private val _overlapRatio = MutableStateFlow(1f)
    val overlapRatio: StateFlow<Float> = _overlapRatio

    private val _selectionMask = MutableStateFlow<Bitmap?>(null)
    val selectionMask: StateFlow<Bitmap?> = _selectionMask

    private val _lastBitmap = MutableStateFlow<Bitmap?>(null)
    val lastBitmap: StateFlow<Bitmap?> = _lastBitmap

    private val _lastMask = MutableStateFlow<Bitmap?>(null)
    val lastMask: StateFlow<Bitmap?> = _lastMask

    private val _lastBsa = MutableStateFlow(0f)
    val lastBsa: StateFlow<Float> = _lastBsa

    val pazienti: Flow<List<PatientConBsa>> = repository.getAllPazientiConBsa()

    /**
     * Imposta il paziente corrente senza avviare una sessione.
     *
     * @param id chiave primaria del paziente
     * @param etaAnni età rappresentativa della fascia d'età (2, 7, 12 o 20)
     */
    fun impostaPatiente(id: Long, etaAnni: Long = 0L) {
        currentPatientId = id
        currentPatientEta = etaAnni
        currentSessionId = -1L
    }

    /**
     * Restituisce la lista dei distretti corporei calibrata per l'età del paziente corrente.
     *
     * @return lista di [BodyRegion] secondo la tabella di Lund-Browder modificata
     */
    fun regionsPerPazienteCorrente(): List<BodyRegion> {
        val ageYears = if (currentPatientEta > 0L) currentPatientEta.toInt() else 20
        return regioniPerEta(ageYears)
    }

    /**
     * Crea una nuova sessione di misurazione su DB per il paziente indicato.
     *
     * @param patientId chiave primaria del paziente
     * @return id della sessione appena creata
     */
    suspend fun creaSessione(patientId: Long): Long {
        val sessionId = repository.creaSessione(patientId)
        currentSessionId = sessionId
        return sessionId
    }

    /**
     * Crea un nuovo paziente su DB e restituisce il suo id.
     *
     * @param nome nome del paziente
     * @param cognome cognome del paziente
     * @param etaAnni età rappresentativa della fascia d'età (2, 7, 12 o 20)
     * @return chiave primaria del paziente inserito
     */
    suspend fun creaPaziente(nome: String, cognome: String, etaAnni: Long = 0L): Long =
        repository.creaPaziente(nome, cognome, etaAnni)

    /**
     * Restituisce il flow delle sessioni con le misure per un paziente.
     *
     * @param patientId id del paziente
     * @return flow delle sessioni
     */
    fun getSessioniConMisurePerPaziente(patientId: Long): Flow<List<SessioneConMisure>> =
        repository.getSessioniConMisurePerPaziente(patientId)

    /**
     * Salva overlay e maschera come JPEG in `filesDir/photos/`, inserisce la misura
     * nel DB e aggiorna le misure in memoria. Non fa nulla se la sessione non è attiva.
     *
     * @param overlay immagine con il risultato della segmentazione
     * @param mask maschera binaria prodotta dal modello ONNX
     * @param bsaPercent BSA calcolato per il distretto corrente
     */
    suspend fun salvaMisuraSuDb(overlay: Bitmap, mask: Bitmap, bsaPercent: Float) {
        val region = _selectedRegion.value ?: return
        val pid = currentPatientId
        val sid = currentSessionId
        if (pid < 0L || sid < 0L) return

        val app = getApplication<Application>()
        val dir = File(app.filesDir, "photos").also { if (!it.exists()) it.mkdirs() }

        val overlayFile = File(dir, "p${pid}_s${sid}_${region.id}_overlay.jpg")
        overlayFile.outputStream().use { out -> overlay.compress(Bitmap.CompressFormat.JPEG, 90, out) }

        val maskFile = File(dir, "p${pid}_s${sid}_${region.id}_mask.jpg")
        maskFile.outputStream().use { out -> mask.compress(Bitmap.CompressFormat.JPEG, 90, out) }

        repository.salvaMisura(
            Measurement(
                patientId = pid,
                sessionId = sid,
                regionId = region.id,
                regionLabel = region.label,
                bsaPercent = bsaPercent,
                photoPath = overlayFile.absolutePath,
                maskPath = maskFile.absolutePath
            )
        )

        addMeasurement(region, bsaPercent)
    }

    /**
     * Imposta la regione corporea selezionata dall'utente sulla mappa.
     *
     * @param region regione scelta in [BodyMapFragment]
     */
    fun selectRegion(region: BodyRegion) {
        _selectedRegion.value = region
    }

    /**
     * Salva il bitmap ritagliato da [CropFragment] nello stato della sessione.
     *
     * @param bmp immagine ritagliata con la silhouette della regione corporea
     */
    fun setCroppedBitmap(bmp: Bitmap) {
        _croppedBitmap.value = bmp
    }

    /**
     * Imposta il fattore di sovrapposizione tra immagine e silhouette.
     *
     * @param ratio fattore di scala applicato al BSA finale
     */
    fun setOverlapRatio(ratio: Float) {
        _overlapRatio.value = ratio
    }

    /**
     * Salva la maschera di selezione manuale disegnata in [SelectionFragment].
     *
     * @param mask bitmap con le aree selezionate a pennello, o null se non usata
     */
    fun setSelectionMask(mask: Bitmap?) {
        _selectionMask.value = mask
    }

    /**
     * Salva il risultato dell'ultima inferenza per la visualizzazione in [ResultFragment].
     *
     * @param bitmap immagine con l'overlay della segmentazione
     * @param mask maschera binaria generata dal modello ONNX
     * @param bsa percentuale BSA calcolata per il distretto
     */
    fun setLastCapture(bitmap: Bitmap, mask: Bitmap, bsa: Float) {
        _lastBitmap.value = bitmap
        _lastMask.value = mask
        _lastBsa.value = bsa
    }

    /**
     * Aggiunge (o aggiorna) la misura in memoria per il distretto indicato.
     *
     * Se esiste già una misura per la stessa regione, viene sostituita.
     * Aggiorna anche [totalBsa] sommando tutti i contributi presenti.
     *
     * @param region distretto anatomico misurato
     * @param bsaPercent contributo percentuale calcolato dall'ONNX
     */
    fun addMeasurement(region: BodyRegion, bsaPercent: Float) {
        val current = _measurements.value.toMutableList()
        current.removeAll { it.region == region }
        current.add(RegionMeasurement(region, bsaPercent))
        _measurements.value = current
        _totalBsa.value = current.sumOf { it.bsaPercent.toDouble() }.toFloat()
    }

    /**
     * Elimina una sessione e i file associati dal filesystem.
     *
     * @param sessionId chiave primaria della sessione da eliminare
     */
    suspend fun eliminaSessione(sessionId: Long) {
        val percorsi = repository.eliminaSessione(sessionId)
        percorsi.forEach { java.io.File(it).delete() }
    }

    /**
     * Elimina un paziente, tutte le sue sessioni e i file associati dal filesystem.
     *
     * @param patientId chiave primaria del paziente da eliminare
     */
    suspend fun eliminaPaziente(patientId: Long) {
        val percorsi = repository.eliminaPaziente(patientId)
        percorsi.forEach { java.io.File(it).delete() }
    }

    /** Azzera tutti i campi della sessione corrente senza toccare il DB. */
    fun resetSession() {
        _measurements.value = emptyList()
        _totalBsa.value = 0f
        _lastBitmap.value = null
        _lastMask.value = null
        _lastBsa.value = 0f
        _selectedRegion.value = null
        _croppedBitmap.value = null
        _overlapRatio.value = 1f
        _selectionMask.value = null
    }
}
