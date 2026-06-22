package com.uninsubria.derma_bsa

import androidx.lifecycle.ViewModel
import com.uninsubria.derma_bsa.model.BodyRegion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RegionMeasurement(
    val region: BodyRegion,
    val bsaContribution: Float
)

class AppViewModel : ViewModel() {

    private val _selectedRegion = MutableStateFlow<BodyRegion?>(null)
    val selectedRegion: StateFlow<BodyRegion?> = _selectedRegion.asStateFlow()

    private val _measurements = MutableStateFlow<List<RegionMeasurement>>(emptyList())
    val measurements: StateFlow<List<RegionMeasurement>> = _measurements.asStateFlow()

    val totalBsa: Float
        get() = _measurements.value.sumOf { it.bsaContribution.toDouble() }.toFloat()

    fun selectRegion(region: BodyRegion) {
        _selectedRegion.value = region
    }

    fun addMeasurement(region: BodyRegion, bsaContribution: Float) {
        val current = _measurements.value.toMutableList()
        current.removeAll { it.region.id == region.id }
        current.add(RegionMeasurement(region, bsaContribution))
        _measurements.value = current
    }

    fun resetSession() {
        _measurements.value = emptyList()
        _selectedRegion.value = null
    }
}