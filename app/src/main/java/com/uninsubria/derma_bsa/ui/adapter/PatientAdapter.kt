package com.uninsubria.derma_bsa.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.uninsubria.derma_bsa.databinding.ItemPatientBinding
import com.uninsubria.derma_bsa.model.PatientConBsa

/**
 * Adapter per la lista dei pazienti nella schermata di selezione.
 *
 * Mostra nome, cognome e percentuale BSA totale di ogni paziente.
 * Utilizza [ListAdapter] con [DiffUtil] per aggiornamenti efficienti
 * della lista quando i dati cambiano nel database.
 *
 * @property onItemClick callback invocato al click su una riga, con il paziente selezionato
 */
class PatientAdapter(
    private val onItemClick: (PatientConBsa) -> Unit
) : ListAdapter<PatientConBsa, PatientAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemPatientBinding) :
        RecyclerView.ViewHolder(binding.root) {

        /**
         * Associa i dati del paziente alle viste della riga.
         *
         * @param paziente paziente da visualizzare
         */
        fun bind(paziente: PatientConBsa) {
            binding.tvNomeCognome.text = "${paziente.nome} ${paziente.cognome}"
            binding.tvBsaTotale.text = "BSA: ${"%.2f".format(paziente.totalBsa)}%"
            binding.root.setOnClickListener { onItemClick(paziente) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPatientBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private companion object DiffCallback : DiffUtil.ItemCallback<PatientConBsa>() {
        override fun areItemsTheSame(old: PatientConBsa, new: PatientConBsa) = old.id == new.id
        override fun areContentsTheSame(old: PatientConBsa, new: PatientConBsa) = old == new
    }
}
