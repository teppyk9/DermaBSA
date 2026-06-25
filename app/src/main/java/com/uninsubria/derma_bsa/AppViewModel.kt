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

data class RegionMeasurement(val region: BodyRegion, val bsaPercent: Float)

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

    // Età in anni del paziente corrente (2/7/12/20 per fascia), 0 se sconosciuta
    var currentPatientBirthdate: Long = 0L
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

    fun impostaPatiente(id: Long, dataNascita: Long = 0L) {
        currentPatientId = id
        currentPatientBirthdate = dataNascita
        currentSessionId = -1L
    }

    fun regionsPerPazienteCorrente(): List<BodyRegion> {
        val ageYears = if (currentPatientBirthdate > 0L) currentPatientBirthdate.toInt() else 20
        return regioniPerEta(ageYears)
    }

    suspend fun creaSessione(patientId: Long): Long {
        val sessionId = repository.creaSessione(patientId)
        currentSessionId = sessionId
        return sessionId
    }

    suspend fun creaPaziente(nome: String, cognome: String, dataNascita: Long = 0L): Long =
        repository.creaPaziente(nome, cognome, dataNascita)

    fun getSessioniConMisurePerPaziente(patientId: Long): Flow<List<SessioneConMisure>> =
        repository.getSessioniConMisurePerPaziente(patientId)

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

    fun selectRegion(region: BodyRegion) {
        _selectedRegion.value = region
    }

    fun setCroppedBitmap(bmp: Bitmap) {
        _croppedBitmap.value = bmp
    }

    fun setOverlapRatio(ratio: Float) {
        _overlapRatio.value = ratio
    }

    fun setSelectionMask(mask: Bitmap?) {
        _selectionMask.value = mask
    }

    fun setLastCapture(bitmap: Bitmap, mask: Bitmap, bsa: Float) {
        _lastBitmap.value = bitmap
        _lastMask.value = mask
        _lastBsa.value = bsa
    }

    fun addMeasurement(region: BodyRegion, bsaPercent: Float) {
        val current = _measurements.value.toMutableList()
        current.removeAll { it.region == region }
        current.add(RegionMeasurement(region, bsaPercent))
        _measurements.value = current
        _totalBsa.value = current.sumOf { it.bsaPercent.toDouble() }.toFloat()
    }

    suspend fun eliminaSessione(sessionId: Long) {
        val percorsi = repository.eliminaSessione(sessionId)
        percorsi.forEach { java.io.File(it).delete() }
    }

    suspend fun eliminaPaziente(patientId: Long) {
        val percorsi = repository.eliminaPaziente(patientId)
        percorsi.forEach { java.io.File(it).delete() }
    }

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
