package com.uninsubria.derma_bsa.ui.fragment

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.uninsubria.derma_bsa.AppViewModel
import com.uninsubria.derma_bsa.databinding.FragmentResultBinding

class ResultFragment : Fragment() {

    private var _binding: FragmentResultBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val original = viewModel.lastBitmap.value
        val mask     = viewModel.lastMask.value
        val bsa      = viewModel.lastBsa.value

        if (original != null && mask != null) {
            // Mostra originale con overlay maschera
            val overlay = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
            val canvas  = Canvas(overlay)
            canvas.drawBitmap(original, 0f, 0f, null)
            val scaledMask = Bitmap.createScaledBitmap(mask, original.width, original.height, false)
            canvas.drawBitmap(scaledMask, 0f, 0f, null)
            binding.imgOriginal.setImageBitmap(overlay)
            binding.imgMask.setImageBitmap(mask)
        }

        binding.tvBsa.text = "BSA: ${"%.2f".format(bsa)}%"

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}