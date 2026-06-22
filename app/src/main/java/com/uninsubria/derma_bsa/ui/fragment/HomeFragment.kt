package com.uninsubria.derma_bsa.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.commit
import com.uninsubria.derma_bsa.AppViewModel
import com.uninsubria.derma_bsa.R
import com.uninsubria.derma_bsa.databinding.FragmentHomeBinding
import com.uninsubria.derma_bsa.model.ALL_REGIONS
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Aggiorna BSA totale in tempo reale
        lifecycleScope.launch {
            viewModel.measurements.collect { measurements ->
                val total = measurements.sumOf { it.bsaContribution.toDouble() }.toFloat()
                binding.textBsaTotal.text = if (total > 0f)
                    "BSA totale: %.1f%%".format(total)
                else
                    "Nessuna misurazione"
            }
        }

        binding.buttonNuovaSessione.setOnClickListener {
            viewModel.resetSession()
        }

        binding.buttonSelezionaDistretto.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.fragment_container, BodyMapFragment())
                addToBackStack(null)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}