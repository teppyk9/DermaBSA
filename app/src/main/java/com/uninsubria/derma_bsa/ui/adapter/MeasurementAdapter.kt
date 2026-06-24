package com.uninsubria.derma_bsa.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.uninsubria.derma_bsa.databinding.ItemMeasurementBinding
import com.uninsubria.derma_bsa.model.Measurement
import java.io.File

/**
 * Adapter per la lista delle misure per distretto nella schermata di dettaglio paziente.
 *
 * Ogni riga mostra il nome del distretto, il contributo BSA e la foto
 * dell'overlay se disponibile su disco.
 */
class MeasurementAdapter : ListAdapter<Measurement, MeasurementAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemMeasurementBinding) :
        RecyclerView.ViewHolder(binding.root) {

        /**
         * Associa i dati della misura alle viste della riga.
         *
         * @param misura misura da visualizzare
         */
        fun bind(misura: Measurement) {
            binding.tvRegione.text = misura.regionLabel
            binding.tvBsaDistretto.text = "BSA distretto: ${"%.2f".format(misura.bsaPercent)}%"

            val photoFile = misura.photoPath?.let { File(it) }
            if (photoFile != null && photoFile.exists()) {
                binding.imgOverlay.visibility = View.VISIBLE
                binding.imgOverlay.load(photoFile)
            } else {
                binding.imgOverlay.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMeasurementBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private companion object DiffCallback : DiffUtil.ItemCallback<Measurement>() {
        override fun areItemsTheSame(old: Measurement, new: Measurement) = old.id == new.id
        override fun areContentsTheSame(old: Measurement, new: Measurement) = old == new
    }
}
