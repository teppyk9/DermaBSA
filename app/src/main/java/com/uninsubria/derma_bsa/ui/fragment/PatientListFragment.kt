package com.uninsubria.derma_bsa.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
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
 * Schermata con la lista dei pazienti passati e il pulsante per iniziare
 * una nuova sessione di misurazione.
 *
 * Ogni riga mostra nome, cognome e percentuale BSA totale. Cliccando su
 * una riga si apre il dettaglio del paziente ([PatientDetailFragment]).
 * Il pulsante "Nuova sessione" mostra un dialogo per inserire nome e
 * cognome del nuovo paziente, poi naviga a [BodyMapFragment].
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

    /**
     * Mostra un dialogo con due campi EditText per inserire nome e cognome
     * del nuovo paziente. Al click su "Crea", crea il paziente nel database
     * e naviga a [BodyMapFragment].
     */
    private fun mostraDialogoNuovoPaziente() {
        val padding = (16 * resources.displayMetrics.density).toInt()

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
        }
        val etNome = EditText(requireContext()).apply { hint = "Nome" }
        val etCognome = EditText(requireContext()).apply { hint = "Cognome" }
        layout.addView(etNome)
        layout.addView(etCognome)

        AlertDialog.Builder(requireContext())
            .setTitle("Nuovo paziente")
            .setView(layout)
            .setPositiveButton("Crea") { _, _ ->
                val nome = etNome.text.toString().trim()
                val cognome = etCognome.text.toString().trim()
                if (nome.isNotEmpty() && cognome.isNotEmpty()) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val id = viewModel.creaPaziente(nome, cognome)
                        viewModel.impostaPatiente(id)
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
     * Apre il dettaglio di un paziente esistente.
     *
     * @param paziente paziente selezionato nella lista
     */
    private fun apriDettaglio(paziente: PatientConBsa) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, PatientDetailFragment.newInstance(
                paziente.id, paziente.nome, paziente.cognome
            ))
            .addToBackStack(null)
            .commit()
    }

    /**
     * Naviga a [BodyMapFragment] per iniziare la misurazione del paziente corrente.
     * Aggiunge il Fragment al back stack con il tag "bodymap" in modo che
     * [ResultFragment] possa tornare qui dopo il salvataggio.
     */
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
