package com.uninsubria.derma_bsa.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.uninsubria.derma_bsa.AppViewModel
import com.uninsubria.derma_bsa.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch

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

        lifecycleScope.launch {
            viewModel.totalBsa.collect { bsa ->
                binding.tvTotalBsa.text = "BSA Totale: ${"%.2f".format(bsa)}%"
            }
        }

        binding.btnCamera.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(com.uninsubria.derma_bsa.R.id.fragment_container, CameraFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.btnReset.setOnClickListener {
            viewModel.resetSession()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}