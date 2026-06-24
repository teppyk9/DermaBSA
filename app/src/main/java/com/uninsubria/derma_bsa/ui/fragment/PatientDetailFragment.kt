package com.uninsubria.derma_bsa.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.uninsubria.derma_bsa.AppViewModel
import com.uninsubria.derma_bsa.R
import com.uninsubria.derma_bsa.databinding.FragmentPatientDetailBinding
import com.uninsubria.derma_bsa.ui.adapter.DetailItem
import com.uninsubria.derma_bsa.ui.adapter.PatientDetailAdapter
import kotlinx.coroutines.launch

/**
 * Schermata di dettaglio di un paziente.
 *
 * Mostra nome, cognome e BSA dell'ultima sessione, seguiti dallo storico
 * completo delle sessioni di visita. Ogni sessione è introdotta da un header
 * con data, BSA totale e pulsante "Elimina" per rimuoverla con conferma.
 * In fondo sono presenti i pulsanti per aggiungere una nuova sessione e
 * per eliminare definitivamente il paziente con tutti i suoi dati.
 */
class PatientDetailFragment : Fragment() {

    private var _binding: FragmentPatientDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPatientDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val patientId = requireArguments().getLong(ARG_PATIENT_ID)
        val nome      = requireArguments().getString(ARG_NOME, "")
        val cognome   = requireArguments().getString(ARG_COGNOME, "")

        binding.tvNomePaziente.text = "$nome $cognome"

        val adapter = PatientDetailAdapter(onDeleteSession = { sessionId ->
            confermaEliminaSessione(sessionId)
        })
        binding.rvMisure.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMisure.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )
        binding.rvMisure.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getSessioniConMisurePerPaziente(patientId).collect { sessioni ->
                val items = mutableListOf<DetailItem>()
                for (sc in sessioni) {
                    val bsaSessione = sc.misure.sumOf { it.bsaPercent.toDouble() }.toFloat()
                    items.add(DetailItem.SessionHeader(sc.sessione, bsaSessione))
                    sc.misure.sortedBy { it.regionLabel }.forEach {
                        items.add(DetailItem.MeasurementItem(it))
                    }
                }
                adapter.submitList(items)

                val bsaUltima = sessioni.firstOrNull()
                    ?.misure?.sumOf { it.bsaPercent.toDouble() }?.toFloat() ?: 0f
                binding.tvBsaTotale.text = "BSA ultima sessione: ${"%.2f".format(bsaUltima)}%"
            }
        }

        binding.btnAggiungiMisurazione.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.impostaPatiente(patientId)
                viewModel.resetSession()
                viewModel.creaSessione(patientId)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, BodyMapFragment())
                    .addToBackStack("bodymap")
                    .commit()
            }
        }

        binding.btnEliminaPaziente.setOnClickListener {
            confermaEliminaPaziente(patientId, "$nome $cognome")
        }
    }

    /**
     * Mostra un dialog di conferma prima di eliminare una sessione.
     * Se confermato, elimina la sessione dal database e i file su disco.
     *
     * @param sessionId id della sessione da eliminare
     */
    private fun confermaEliminaSessione(sessionId: Long) {
        AlertDialog.Builder(requireContext())
            .setTitle("Elimina sessione")
            .setMessage("Vuoi eliminare questa sessione e tutte le misure associate?")
            .setPositiveButton("Elimina") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.eliminaSessione(sessionId)
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    /**
     * Mostra un dialog di conferma prima di eliminare il paziente.
     * Se confermato, elimina il paziente con tutte le sessioni e misure,
     * cancella i file su disco e torna alla schermata precedente.
     *
     * @param patientId id del paziente da eliminare
     * @param nomeCognome nome visualizzato nel messaggio di conferma
     */
    private fun confermaEliminaPaziente(patientId: Long, nomeCognome: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Elimina paziente")
            .setMessage("Vuoi eliminare $nomeCognome con tutte le sessioni e misure? L'operazione non è reversibile.")
            .setPositiveButton("Elimina") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.eliminaPaziente(patientId)
                    parentFragmentManager.popBackStack()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_PATIENT_ID = "patient_id"
        private const val ARG_NOME = "nome"
        private const val ARG_COGNOME = "cognome"

        /**
         * Crea una nuova istanza di [PatientDetailFragment] con gli argomenti necessari.
         *
         * @param patientId id del paziente da visualizzare
         * @param nome nome del paziente
         * @param cognome cognome del paziente
         */
        fun newInstance(patientId: Long, nome: String, cognome: String) =
            PatientDetailFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_PATIENT_ID, patientId)
                    putString(ARG_NOME, nome)
                    putString(ARG_COGNOME, cognome)
                }
            }
    }
}
