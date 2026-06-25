package com.uninsubria.derma_bsa.ui.fragment

import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.uninsubria.derma_bsa.AppViewModel
import com.uninsubria.derma_bsa.R
import com.uninsubria.derma_bsa.databinding.FragmentBodyMapBinding
import com.uninsubria.derma_bsa.model.BodyRegion

/**
 * Primo step del flusso di misurazione: selezione del distretto anatomico.
 *
 * Mostra il corpo umano (vista fronte o retro) tramite drawable SVG cliccabili.
 * Il distretto toccato viene evidenziato con un tint blu; il bottone "Continua"
 * si abilita solo dopo la selezione.
 */
class BodyMapFragment : Fragment() {

    private var _binding: FragmentBodyMapBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppViewModel by activityViewModels()

    private var selezione: BodyRegion? = null
    private var selectedView: ImageView? = null
    private var isFront = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBodyMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val regions = viewModel.regionsPerPazienteCorrente()

        // Registra i click su tutti gli ImageView del pannello fronte
        registraClick(binding.bodyFront, isFrontPanel = true, regions)

        // Registra i click su tutti gli ImageView del pannello retro
        registraClick(binding.bodyBack, isFrontPanel = false, regions)

        binding.toggleVista.setOnCheckedChangeListener { _, checkedId ->
            isFront = (checkedId == R.id.rbFronte)
            binding.bodyFront.visibility = if (isFront) View.VISIBLE else View.GONE
            binding.bodyBack.visibility  = if (isFront) View.GONE  else View.VISIBLE
            // Deseleziona quando si cambia lato
            clearSelection()
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

    /**
     * Itera tutti i figli ImageView di un pannello (front o back) e
     * assegna a ciascuno il click listener che seleziona il distretto corrispondente.
     */
    private fun registraClick(
        pannello: ConstraintLayout,
        isFrontPanel: Boolean,
        regions: List<BodyRegion>
    ) {
        for (i in 0 until pannello.childCount) {
            val child = pannello.getChildAt(i) as? ImageView ?: continue
            val tag = child.tag as? String ?: continue
            val regionId = tagToRegionId(tag, isFrontPanel)
            val region = regions.firstOrNull { it.id == regionId } ?: continue

            child.setOnClickListener {
                selezionaDistretto(child, region)
            }
        }
    }

    /**
     * Converte il tag XML dell'ImageView nell'ID del distretto anatomico
     * aggiungendo il suffisso "_front" o "_back" dove necessario.
     *
     * Tag già completi (invariati): torso_front, torso_back, groin,
     * gluteus_left, gluteus_right.
     * Tag semplici (head, neck, upper_arm_left, ...): si aggiunge il suffisso.
     */
    private fun tagToRegionId(tag: String, isFrontPanel: Boolean): String {
        return when (tag) {
            "torso_front", "torso_back",
            "groin", "gluteus_left", "gluteus_right" -> tag
            else -> "${tag}_${if (isFrontPanel) "front" else "back"}"
        }
    }

    private fun selezionaDistretto(imageView: ImageView, region: BodyRegion) {
        // Rimuove evidenziazione precedente
        selectedView?.clearColorFilter()

        // Applica tint di selezione
        imageView.setColorFilter(Color.argb(160, 21, 101, 192), PorterDuff.Mode.SRC_ATOP)
        selectedView = imageView
        selezione = region

        val bsaStr = if (region.bsaPercent % 1f == 0f)
            "${region.bsaPercent.toInt()}" else "%.2f".format(region.bsaPercent)
        binding.tvSelezione.text = "Selezionato: ${region.label} · $bsaStr%"
        binding.btnContinua.isEnabled = true
    }

    private fun clearSelection() {
        selectedView?.clearColorFilter()
        selectedView = null
        selezione = null
        binding.tvSelezione.text = "Nessun distretto selezionato"
        binding.btnContinua.isEnabled = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
