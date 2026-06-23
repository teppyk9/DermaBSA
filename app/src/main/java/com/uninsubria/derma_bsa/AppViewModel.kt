package com.uninsubria.derma_bsa

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import com.uninsubria.derma_bsa.model.BodyRegion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class RegionMeasurement(val region: BodyRegion, val bsaPercent: Float)

class AppViewModel : ViewModel() {

    private val _selectedRegion = MutableStateFlow<BodyRegion?>(null)
    val selectedRegion: StateFlow<BodyRegion?> = _selectedRegion

    private val _measurements = MutableStateFlow<List<RegionMeasurement>>(emptyList())
    val measurements: StateFlow<List<RegionMeasurement>> = _measurements

    private val _totalBsa = MutableStateFlow(0f)
    val totalBsa: StateFlow<Float> = _totalBsa

    private val _lastBitmap = MutableStateFlow<Bitmap?>(null)
    val lastBitmap: StateFlow<Bitmap?> = _lastBitmap

    private val _lastMask = MutableStateFlow<Bitmap?>(null)
    val lastMask: StateFlow<Bitmap?> = _lastMask

    private val _lastBsa = MutableStateFlow(0f)
    val lastBsa: StateFlow<Float> = _lastBsa

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

    fun resetSession() {
        _measurements.value = emptyList()
        _totalBsa.value = 0f
        _lastBitmap.value = null
        _lastMask.value = null
        _lastBsa.value = 0f
    }
}