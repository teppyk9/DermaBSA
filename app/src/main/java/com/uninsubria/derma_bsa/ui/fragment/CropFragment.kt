package com.uninsubria.derma_bsa.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.uninsubria.derma_bsa.AppViewModel
import com.uninsubria.derma_bsa.R
import com.uninsubria.derma_bsa.databinding.FragmentCropBinding

/**
 * Terzo step del flusso di misurazione: ritaglio dell'immagine.
 *
 * Mostra la foto acquisita con sovrapposta la sagoma del distretto selezionato.
 * L'utente posiziona, ridimensiona e ruota la sagoma finché non copre
 * correttamente la zona da analizzare, poi conferma il ritaglio.
 * L'immagine ritagliata viene salvata nel ViewModel e si naviga a [SelectionFragment].
 */
class CropFragment : Fragment() {

    private var _binding: FragmentCropBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCropBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val region = viewModel.selectedRegion.value
        binding.tvDistrettoCrop.text = "Posiziona la sagoma: ${region?.label ?: "-"}"

        val bitmap = viewModel.croppedBitmap.value
        if (bitmap == null) {
            Toast.makeText(requireContext(), "Nessuna immagine disponibile", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        binding.cropOverlayView.regionId = region?.id ?: "torso_front"
        binding.cropOverlayView.image = bitmap

        binding.btnCropBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnCropConfirm.setOnClickListener {
            val cropResult = binding.cropOverlayView.cropImage()
            if (cropResult == null) {
                Toast.makeText(requireContext(), "Ritaglio non riuscito", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val (cropped, overlapRatio) = cropResult
            viewModel.setCroppedBitmap(cropped)
            viewModel.setOverlapRatio(overlapRatio)
            viewModel.setSelectionMask(null)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SelectionFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
