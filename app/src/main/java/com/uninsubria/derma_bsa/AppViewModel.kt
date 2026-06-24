package com.uninsubria.derma_bsa

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import com.uninsubria.derma_bsa.model.BodyRegion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Rappresenta la misura di un singolo distretto anatomico.
 *
 * @property region distretto anatomico misurato
 * @property bsaPercent contributo percentuale al BSA totale
 */
data class RegionMeasurement(val region: BodyRegion, val bsaPercent: Float)

/**
 * ViewModel condiviso tra tutti i Fragment della sessione di misurazione.
 *
 * Mantiene in memoria lo stato corrente: il distretto selezionato, le misure
 * accumulate, i bitmap intermedi e i risultati finali. Tutti i dati vengono
 * azzerati da [resetSession] quando si inizia una nuova sessione.
 */
class AppViewModel : ViewModel() {

    /** Distretto anatomico selezionato sulla mappa corporea. */
    private val _selectedRegion = MutableStateFlow<BodyRegion?>(null)
    val selectedRegion: StateFlow<BodyRegion?> = _selectedRegion

    /** Lista delle misure per distretto accumulate nella sessione corrente. */
    private val _measurements = MutableStateFlow<List<RegionMeasurement>>(emptyList())
    val measurements: StateFlow<List<RegionMeasurement>> = _measurements

    /** Somma dei contributi BSA di tutti i distretti misurati. */
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
     * Aggiunge o aggiorna la misura del distretto specificato.
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
     * Azzera tutti i dati della sessione corrente.
     * Da chiamare quando si vuole iniziare una nuova misurazione da zero.
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
