package com.uninsubria.derma_bsa.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.uninsubria.derma_bsa.AppViewModel
import com.uninsubria.derma_bsa.R
import com.uninsubria.derma_bsa.databinding.FragmentBodyMapBinding
import com.uninsubria.derma_bsa.model.BodyRegion

/**
 * Primo step del flusso di misurazione: selezione del distretto anatomico.
 *
 * Mostra la mappa anatomica del corpo umano (fronte o retro) e permette
 * all'utente di toccare il distretto da misurare. Il bottone "Continua"
 * si abilita solo dopo che un distretto è stato selezionato.
 */
class BodyMapFragment : Fragment() {

    private var _binding: FragmentBodyMapBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppViewModel by activityViewModels()

    private var selezione: BodyRegion? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBodyMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.bodyMap.onRegionSelected = { region ->
            selezione = region
            binding.tvSelezione.text = "Selezionato: ${region.label} · ${region.bsaPercent.toInt()}%"
            binding.btnContinua.isEnabled = true
        }

        binding.toggleVista.setOnCheckedChangeListener { _, checkedId ->
            binding.bodyMap.retro = (checkedId == R.id.rbRetro)
            selezione = null
            binding.tvSelezione.text = "Nessun distretto selezionato"
            binding.btnContinua.isEnabled = false
        }

        binding.btnAnnulla.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnContinua.setOnClickListener {
            val region = selezione ?: return@setOnClickListener
            viewModel.selectRegion(region)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, CameraFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
