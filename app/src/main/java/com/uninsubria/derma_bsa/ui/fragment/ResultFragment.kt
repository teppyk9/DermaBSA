package com.uninsubria.derma_bsa.ui.fragment

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.uninsubria.derma_bsa.AppViewModel
import com.uninsubria.derma_bsa.databinding.FragmentResultBinding
import kotlinx.coroutines.launch

/**
 * Quinto e ultimo step del flusso di misurazione: visualizzazione dei risultati.
 *
 * Mostra l'immagine del distretto con la maschera di segmentazione sovrapposta,
 * la maschera sola e il valore BSA calcolato.
 * "Salva nella sessione" scrive la misura e le foto su database e torna a
 * [BodyMapFragment] per consentire di misurare altri distretti dello stesso paziente.
 * "Scarta e torna" torna a [BodyMapFragment] senza salvare.
 */
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
        val region   = viewModel.selectedRegion.value

        binding.tvDistretto.text = "Distretto: ${region?.label ?: "-"}"

        if (original != null && mask != null) {
            binding.imgOriginal.setImageBitmap(creaOverlay(original, mask))
            binding.imgMask.setImageBitmap(
                Bitmap.createScaledBitmap(mask, original.width, original.height, true)
            )
        }

        binding.tvBsa.text = "BSA distretto: ${"%.2f".format(bsa)}%"

        binding.btnSalva.setOnClickListener {
            if (region != null && original != null && mask != null) {
                val overlay = creaOverlay(original, mask)
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        viewModel.salvaMisuraSuDb(overlay, mask, bsa)
                        Toast.makeText(requireContext(), "Misura salvata", Toast.LENGTH_SHORT).show()
                        tornaAllaMappaCorpo()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Errore durante il salvataggio: riprova", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                tornaAllaMappaCorpo()
            }
        }

        binding.btnBack.setOnClickListener { tornaAllaMappaCorpo() }
    }

    /**
     * Compone l'immagine originale con la maschera di segmentazione sovrapposta.
     * La maschera viene scalata alle dimensioni dell'immagine originale prima
     * di essere disegnata sopra.
     *
     * @param original immagine del distretto ritagliata
     * @param mask maschera 256×256 prodotta da derma_seg
     * @return bitmap con la maschera sovrapposta all'immagine originale
     */
    private fun creaOverlay(original: Bitmap, mask: Bitmap): Bitmap {
        val overlay = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val canvas  = Canvas(overlay)
        canvas.drawBitmap(original, 0f, 0f, null)
        val scaledMask = Bitmap.createScaledBitmap(mask, original.width, original.height, false)
        canvas.drawBitmap(scaledMask, 0f, 0f, null)
        return overlay
    }

    /**
     * Torna a [BodyMapFragment] svuotando il back stack fino al tag "bodymap"
     * (esclusivo), in modo che l'utente possa misurare un altro distretto
     * per lo stesso paziente o tornare alla lista pazienti premendo indietro.
     */
    private fun tornaAllaMappaCorpo() {
        parentFragmentManager.popBackStack("bodymap", 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}