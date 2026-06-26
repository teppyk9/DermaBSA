package com.uninsubria.derma_bsa.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.uninsubria.derma_bsa.AppViewModel
import com.uninsubria.derma_bsa.R
import com.uninsubria.derma_bsa.databinding.FragmentPatientListBinding
import com.uninsubria.derma_bsa.model.PatientConBsa
import com.uninsubria.derma_bsa.ui.adapter.PatientAdapter
import kotlinx.coroutines.launch

/**
 * Schermata principale con la lista dei pazienti salvati.
 * Ogni riga mostra nome, cognome e BSA dell'ultima sessione.
 * Dal pulsante in basso si crea un nuovo paziente e si avvia la misurazione.
 */
class PatientListFragment : Fragment() {

    private var _binding: FragmentPatientListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppViewModel by activityViewModels()
    private lateinit var adapter: PatientAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPatientListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PatientAdapter { paziente -> apriDettaglio(paziente) }
        binding.rvPazienti.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPazienti.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )
        binding.rvPazienti.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.pazienti.collect { lista ->
                adapter.submitList(lista)
                binding.tvEmpty.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        binding.btnNuovaSessione.setOnClickListener { mostraDialogoNuovoPaziente() }
    }

    /** Apre il dialogo di creazione paziente e naviga alla mappa corporea. */
    private fun mostraDialogoNuovoPaziente() {
        val padding = (16 * resources.displayMetrics.density).toInt()

        val fasce = arrayOf("0-4 anni", "5-9 anni", "10-14 anni", "15+ anni")
        val etaPerFascia = longArrayOf(2L, 7L, 12L, 20L)

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
        }
        val etNome = EditText(requireContext()).apply { hint = "Nome" }
        val etCognome = EditText(requireContext()).apply { hint = "Cognome" }
        val tvFascia = TextView(requireContext()).apply {
            text = "Fascia d'età"
            setPadding(0, padding / 2, 0, 0)
        }
        val spinner = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                fasce
            )
            setSelection(3) // default: 15+ anni
        }

        layout.addView(etNome)
        layout.addView(etCognome)
        layout.addView(tvFascia)
        layout.addView(spinner)

        AlertDialog.Builder(requireContext())
            .setTitle("Nuovo paziente")
            .setView(layout)
            .setPositiveButton("Crea") { _, _ ->
                val nome = etNome.text.toString().trim()
                val cognome = etCognome.text.toString().trim()
                if (nome.isNotEmpty() && cognome.isNotEmpty()) {
                    val eta = etaPerFascia[spinner.selectedItemPosition]
                    viewLifecycleOwner.lifecycleScope.launch {
                        val id = viewModel.creaPaziente(nome, cognome, eta)
                        viewModel.impostaPatiente(id, eta)
                        viewModel.resetSession()
                        viewModel.creaSessione(id)
                        avviaBodyMap()
                    }
                } else {
                    Toast.makeText(requireContext(), "Inserisci nome e cognome", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    /**
     * Apre [PatientDetailFragment] per il paziente selezionato dalla lista.
     *
     * @param paziente il paziente su cui è stato fatto tap
     */
    private fun apriDettaglio(paziente: PatientConBsa) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, PatientDetailFragment.newInstance(
                paziente.id, paziente.nome, paziente.cognome, paziente.etaAnni
            ))
            .addToBackStack(null)
            .commit()
    }

    /** Naviga a [BodyMapFragment] per avviare la selezione della regione corporea. */
    private fun avviaBodyMap() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, BodyMapFragment())
            .addToBackStack("bodymap")
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
