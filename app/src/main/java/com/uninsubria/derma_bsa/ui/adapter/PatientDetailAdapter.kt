package com.uninsubria.derma_bsa.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.uninsubria.derma_bsa.databinding.ItemMeasurementBinding
import com.uninsubria.derma_bsa.databinding.ItemSessionHeaderBinding
import com.uninsubria.derma_bsa.model.Measurement
import com.uninsubria.derma_bsa.model.Session
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Elemento della lista nel dettaglio paziente.
 * Può essere un header di sessione o una riga di misura per distretto.
 */
sealed class DetailItem {
    /** Header che introduce una sessione di visita. */
    data class SessionHeader(val sessione: Session, val bsaTotale: Float) : DetailItem()
    /** Riga con i dati di una singola misura per distretto. */
    data class MeasurementItem(val misura: Measurement) : DetailItem()
}

/**
 * Adapter per la schermata di dettaglio paziente.
 *
 * Gestisce due tipi di riga: header di sessione (data + BSA totale sessione)
 * e misura per distretto (nome distretto + BSA + foto overlay).
 * Le sessioni sono mostrate dalla più recente alla più vecchia.
 *
 * @property onDeleteSession callback invocato quando l'utente preme "Elimina" su una sessione
 */
class PatientDetailAdapter(
    private val onDeleteSession: (sessionId: Long) -> Unit
) : ListAdapter<DetailItem, RecyclerView.ViewHolder>(DiffCallback) {

    inner class SessionHeaderViewHolder(private val binding: ItemSessionHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        /**
         * Associa i dati della sessione all'header.
         *
         * @param item header con sessione e BSA totale
         */
        fun bind(item: DetailItem.SessionHeader) {
            val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            binding.tvSessioneData.text = "Sessione: ${fmt.format(Date(item.sessione.dataOra))}"
            binding.tvBsaSessione.text = "BSA sessione: ${"%.2f".format(item.bsaTotale)}%"
            binding.btnEliminaSessione.setOnClickListener { onDeleteSession(item.sessione.id) }
        }
    }

    inner class MeasurementViewHolder(private val binding: ItemMeasurementBinding) :
        RecyclerView.ViewHolder(binding.root) {

        /**
         * Associa i dati della misura alla riga.
         *
         * @param item misura con distretto, BSA e percorso foto
         */
        fun bind(item: DetailItem.MeasurementItem) {
            val misura = item.misura
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

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is DetailItem.SessionHeader -> TYPE_HEADER
        is DetailItem.MeasurementItem -> TYPE_MEASUREMENT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> SessionHeaderViewHolder(
                ItemSessionHeaderBinding.inflate(inflater, parent, false)
            )
            else -> MeasurementViewHolder(
                ItemMeasurementBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is DetailItem.SessionHeader -> (holder as SessionHeaderViewHolder).bind(item)
            is DetailItem.MeasurementItem -> (holder as MeasurementViewHolder).bind(item)
        }
    }

    private companion object DiffCallback : DiffUtil.ItemCallback<DetailItem>() {
        const val TYPE_HEADER = 0
        const val TYPE_MEASUREMENT = 1

        override fun areItemsTheSame(old: DetailItem, new: DetailItem): Boolean = when {
            old is DetailItem.SessionHeader && new is DetailItem.SessionHeader ->
                old.sessione.id == new.sessione.id
            old is DetailItem.MeasurementItem && new is DetailItem.MeasurementItem ->
                old.misura.id == new.misura.id
            else -> false
        }

        override fun areContentsTheSame(old: DetailItem, new: DetailItem): Boolean = old == new
    }
}
