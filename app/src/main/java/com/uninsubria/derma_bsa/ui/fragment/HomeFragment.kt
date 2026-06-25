package com.uninsubria.derma_bsa.ui.fragment

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.uninsubria.derma_bsa.AppViewModel
import com.uninsubria.derma_bsa.R
import com.uninsubria.derma_bsa.databinding.FragmentHomeBinding
import com.uninsubria.derma_bsa.util.BsaCalculator
import kotlinx.coroutines.launch

/**
 * Schermata principale dell'applicazione.
 *
 * Mostra il BSA totale della sessione corrente, l'elenco dei distretti già misurati
 * con i rispettivi contributi, e i valori PASI suddivisi per macro-regione.
 * Da qui l'utente può avviare una nuova misurazione o azzerare la sessione.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.measurements.collect { misure ->
                aggiornaUi(misure)
            }
        }

        binding.btnNuovaMisura.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, BodyMapFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.btnReset.setOnClickListener {
            viewModel.resetSession()
        }
    }

    /**
     * Aggiorna tutta la parte dinamica dell'interfaccia in risposta
     * a una modifica della lista delle misure.
     *
     * @param misure lista aggiornata delle misure della sessione
     */
    private fun aggiornaUi(misure: List<com.uninsubria.derma_bsa.RegionMeasurement>) {
        binding.tvTotalBsa.text = "BSA Totale: ${"%.2f".format(BsaCalculator.bsaTotale(misure))}%"

        binding.containerMisure.removeAllViews()
        if (misure.isEmpty()) {
            binding.containerMisure.addView(rigaTesto("Nessun distretto misurato."))
        } else {
            misure.forEach { m ->
                binding.containerMisure.addView(
                    rigaTesto("${m.region.label}: contributo ${"%.2f".format(m.bsaPercent)}% BSA")
                )
            }
        }

        val pasi = BsaCalculator.percentualiPasi(misure, viewModel.regionsPerPazienteCorrente())
        binding.tvPasi.text = if (pasi.isEmpty()) "-" else {
            pasi.entries.joinToString("\n") { (reg, perc) ->
                "${reg.label}: ${"%.1f".format(perc)}% → area score ${BsaCalculator.pasiAreaScore(perc)}"
            }
        }
    }

    /**
     * Crea una riga di testo semplice da aggiungere al contenitore delle misure.
     *
     * @param testo contenuto della riga
     * @return [TextView] già configurata con padding e dimensione del testo
     */
    private fun rigaTesto(testo: String) = TextView(requireContext()).apply {
        text = testo
        textSize = 14f
        gravity = Gravity.START
        setPadding(0, 8, 0, 8)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
