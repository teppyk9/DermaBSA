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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Rappresenta la misura di un singolo distretto anatomico nella sessione corrente.
 *
 * @property region distretto anatomico misurato
 * @property bsaPercent contributo percentuale al BSA totale
 */
data class RegionMeasurement(val region: BodyRegion, val bsaPercent: Float)

/**
 * ViewModel condiviso tra tutti i Fragment dell'applicazione.
 *
 * Gestisce sia lo stato della sessione di misurazione corrente (in memoria)
 * sia l'accesso al database tramite [PatientRepository]. Estende
 * [AndroidViewModel] per poter accedere al contesto dell'applicazione,
 * necessario per creare il database e salvare le foto su disco.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PatientRepository

    init {
        val db = AppDatabase.getInstance(application)
        repository = PatientRepository(db.patientDao(), db.sessionDao(), db.measurementDao())
    }

    /** Id del paziente corrente; -1 se nessun paziente è selezionato. */
    var currentPatientId: Long = -1L
        private set

    /** Id della sessione corrente; -1 se nessuna sessione è stata creata. */
    var currentSessionId: Long = -1L
        private set

    /** Distretto anatomico selezionato sulla mappa corporea. */
    private val _selectedRegion = MutableStateFlow<BodyRegion?>(null)
    val selectedRegion: StateFlow<BodyRegion?> = _selectedRegion

    /** Lista delle misure per distretto accumulate nella sessione corrente (in memoria). */
    private val _measurements = MutableStateFlow<List<RegionMeasurement>>(emptyList())
    val measurements: StateFlow<List<RegionMeasurement>> = _measurements

    /** Somma dei contributi BSA di tutti i distretti misurati nella sessione corrente. */
    private val _totalBsa = MutableStateFlow(0f)
    val totalBsa: StateFlow<Float> = _totalBsa

    /** Foto ritagliata nella forma del distretto, pronta per la segmentazione. */
    private val _croppedBitmap = MutableStateFlow<Bitmap?>(null)
    val croppedBitmap: StateFlow<Bitmap?> = _croppedBitmap

    /**
     * Maschera disegnata manualmente dall'utente con il pennello.
     * Se `null`, la segmentazione viene eseguita sull'intera immagine (auto detect).
     */
    private val _selectionMask = MutableStateFlow<Bitmap?>(null)
    val selectionMask: StateFlow<Bitmap?> = _selectionMask

    /** Immagine mostrata nella schermata dei risultati. */
    private val _lastBitmap = MutableStateFlow<Bitmap?>(null)
    val lastBitmap: StateFlow<Bitmap?> = _lastBitmap

    /** Maschera di segmentazione sovrapposta all'immagine nei risultati. */
    private val _lastMask = MutableStateFlow<Bitmap?>(null)
    val lastMask: StateFlow<Bitmap?> = _lastMask

    /** Valore BSA calcolato per l'ultima misura, mostrato nei risultati. */
    private val _lastBsa = MutableStateFlow(0f)
    val lastBsa: StateFlow<Float> = _lastBsa

    /**
     * Flow reattivo con tutti i pazienti e il BSA dell'ultima sessione,
     * usato dalla lista pazienti.
     */
    val pazienti: Flow<List<PatientConBsa>> = repository.getAllPazientiConBsa()

    /**
     * Imposta il paziente corrente per la sessione di misurazione.
     * Azzera anche la sessione corrente in attesa che venga creata una nuova.
     *
     * @param id id del paziente selezionato o appena creato
     */
    fun impostaPatiente(id: Long) {
        currentPatientId = id
        currentSessionId = -1L
    }

    /**
     * Crea una nuova sessione di visita per il paziente corrente nel database
     * e la imposta come sessione corrente.
     *
     * @param patientId id del paziente per cui creare la sessione
     * @return id della sessione appena creata
     */
    suspend fun creaSessione(patientId: Long): Long {
        val sessionId = repository.creaSessione(patientId)
        currentSessionId = sessionId
        return sessionId
    }

    /**
     * Crea un nuovo paziente nel database e restituisce l'id assegnato.
     *
     * @param nome nome del paziente
     * @param cognome cognome del paziente
     * @return id del paziente appena creato
     */
    suspend fun creaPaziente(nome: String, cognome: String): Long =
        repository.creaPaziente(nome, cognome)

    /**
     * Restituisce le sessioni di un paziente con le relative misure come Flow reattivo,
     * ordinate dalla più recente alla più vecchia. Usato dal dettaglio paziente.
     *
     * @param patientId id del paziente
     */
    fun getSessioniConMisurePerPaziente(patientId: Long): Flow<List<SessioneConMisure>> =
        repository.getSessioniConMisurePerPaziente(patientId)

    /**
     * Salva i risultati dell'ultima inferenza su database e su disco.
     *
     * Salva l'immagine con overlay e la maschera come file JPEG in
     * `filesDir/photos/`, poi inserisce il record nel database.
     * All'interno della stessa sessione, se esiste già una misura per lo
     * stesso distretto, viene sostituita. Sessioni diverse conservano
     * le proprie misure indipendentemente.
     *
     * @param overlay bitmap con la maschera rossa sovrapposta all'immagine originale
     * @param mask maschera 256×256 prodotta da derma_seg
     * @param bsaPercent contributo BSA calcolato per il distretto corrente
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
     * Salva il distretto scelto dall'utente sulla mappa anatomica.
     *
     * @param region distretto selezionato
     */
    fun selectRegion(region: BodyRegion) {
        _selectedRegion.value = region
    }

    /**
     * Salva il bitmap ritagliato nella forma del distretto dopo il passo di crop.
     *
     * @param bmp immagine ritagliata
     */
    fun setCroppedBitmap(bmp: Bitmap) {
        _croppedBitmap.value = bmp
    }

    /**
     * Salva la maschera di selezione manuale disegnata dall'utente.
     * Passare `null` equivale a scegliere il rilevamento automatico.
     *
     * @param mask maschera bianca su sfondo trasparente, oppure `null`
     */
    fun setSelectionMask(mask: Bitmap?) {
        _selectionMask.value = mask
    }

    /**
     * Salva i risultati dell'ultima inferenza per mostrarli nel [ui.fragment.ResultFragment].
     *
     * @param bitmap immagine su cui è stata eseguita la segmentazione
     * @param mask maschera rossa semitrasparente prodotta da derma_seg
     * @param bsa valore BSA calcolato per il distretto corrente
     */
    fun setLastCapture(bitmap: Bitmap, mask: Bitmap, bsa: Float) {
        _lastBitmap.value = bitmap
        _lastMask.value = mask
        _lastBsa.value = bsa
    }

    /**
     * Aggiunge o aggiorna la misura del distretto nella lista in memoria.
     * Se esiste già una misura per lo stesso distretto, viene sostituita.
     *
     * @param region distretto anatomico misurato
     * @param bsaPercent contributo BSA calcolato
     */
    fun addMeasurement(region: BodyRegion, bsaPercent: Float) {
        val current = _measurements.value.toMutableList()
        current.removeAll { it.region == region }
        current.add(RegionMeasurement(region, bsaPercent))
        _measurements.value = current
        _totalBsa.value = current.sumOf { it.bsaPercent.toDouble() }.toFloat()
    }

    /**
     * Elimina una sessione dal database e cancella i file foto/maschera su disco.
     *
     * @param sessionId id della sessione da eliminare
     */
    suspend fun eliminaSessione(sessionId: Long) {
        val percorsi = repository.eliminaSessione(sessionId)
        percorsi.forEach { java.io.File(it).delete() }
    }

    /**
     * Elimina un paziente dal database con tutte le sue sessioni e misure,
     * e cancella i file foto/maschera su disco.
     *
     * @param patientId id del paziente da eliminare
     */
    suspend fun eliminaPaziente(patientId: Long) {
        val percorsi = repository.eliminaPaziente(patientId)
        percorsi.forEach { java.io.File(it).delete() }
    }

    /**
     * Azzera lo stato della sessione corrente in memoria.
     * Non elimina i dati dal database né cambia il paziente corrente.
     */
    fun resetSession() {
        _measurements.value = emptyList()
        _totalBsa.value = 0f
        _lastBitmap.value = null
        _lastMask.value = null
        _lastBsa.value = 0f
        _selectedRegion.value = null
        _croppedBitmap.value = null
        _selectionMask.value = null
    }
}
