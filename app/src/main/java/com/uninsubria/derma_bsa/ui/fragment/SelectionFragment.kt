package com.uninsubria.derma_bsa.ui.fragment

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.uninsubria.derma_bsa.AppViewModel
import com.uninsubria.derma_bsa.R
import com.uninsubria.derma_bsa.databinding.FragmentSelectionBinding
import com.uninsubria.derma_bsa.ui.view.SelectionCanvasView
import com.uninsubria.derma_bsa.util.OnnxHelper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Quarto step del flusso di misurazione: selezione dell'area da analizzare.
 *
 * L'utente può dipingere manualmente la zona interessata usando pennello e gomma
 * con dimensione regolabile, oppure scegliere il rilevamento automatico.
 * Premendo "Analizza" viene avviata l'inferenza ONNX e si naviga a [ResultFragment].
 *
 * Se è presente una selezione manuale, il modello analizzerà solo quell'area;
 * altrimenti la segmentazione viene eseguita sull'intera immagine ritagliata.
 */
class SelectionFragment : Fragment() {

    private var _binding: FragmentSelectionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppViewModel by activityViewModels()
    private lateinit var executor: ExecutorService

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        executor = Executors.newSingleThreadExecutor()

        val cropped = viewModel.croppedBitmap.value
        if (cropped == null) {
            Toast.makeText(requireContext(), "Nessuna immagine disponibile", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }
        binding.ivCroppedPreview.setImageBitmap(cropped)

        configuraTool()
        configuraSeekBar()

        binding.btnIndietro.setOnClickListener { parentFragmentManager.popBackStack() }

        binding.btnClearSelection.setOnClickListener {
            binding.selectionCanvas.clearSelection()
        }

        binding.btnAutoDetect.setOnClickListener {
            binding.selectionCanvas.clearSelection()
            avviaAnalisi(selectionMask = null)
        }

        binding.btnAnalyze.setOnClickListener {
            val mask = if (binding.selectionCanvas.hasSelection())
                binding.selectionCanvas.getSelectionMask()
            else null
            avviaAnalisi(selectionMask = mask)
        }
    }

    /**
     * Configura i ToggleButton per la scelta dello strumento (pennello / gomma),
     * assicurandosi che solo uno alla volta risulti attivo.
     */
    private fun configuraTool() {
        binding.btnBrush.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                binding.selectionCanvas.activeTool = SelectionCanvasView.Tool.BRUSH
                binding.btnEraser.isChecked = false
            }
        }
        binding.btnEraser.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                binding.selectionCanvas.activeTool = SelectionCanvasView.Tool.ERASER
                binding.btnBrush.isChecked = false
            }
        }
    }

    /**
     * Collega la SeekBar alla dimensione del pennello/gomma nel canvas.
     * Il valore minimo è 10 pixel per evitare che il tratto diventi invisibile.
     */
    private fun configuraSeekBar() {
        binding.seekBrushSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                binding.selectionCanvas.brushSize = (progress + 10).toFloat()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        binding.selectionCanvas.brushSize = (binding.seekBrushSize.progress + 10).toFloat()
    }

    /**
     * Avvia la segmentazione su un thread separato per non bloccare l'interfaccia.
     * Al termine salva i risultati nel ViewModel e naviga a [ResultFragment].
     *
     * @param selectionMask maschera manuale dell'utente, oppure `null` per l'auto detect
     */
    private fun avviaAnalisi(selectionMask: Bitmap?) {
        val cropped = viewModel.croppedBitmap.value ?: return
        val regionBsaPercent = viewModel.selectedRegion.value?.bsaPercent ?: 18f

        binding.btnAnalyze.isEnabled    = false
        binding.btnAutoDetect.isEnabled = false

        executor.execute {
            val mask = OnnxHelper.segmentWithMask(cropped, selectionMask)
            val bsa  = OnnxHelper.calcBsa(mask, regionBsaPercent)
            viewModel.setLastCapture(cropped, mask, bsa)
            requireActivity().runOnUiThread {
                binding.btnAnalyze.isEnabled    = true
                binding.btnAutoDetect.isEnabled = true
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, ResultFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        executor.shutdown()
        _binding = null
    }
}
