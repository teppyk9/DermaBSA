package com.uninsubria.derma_bsa.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.uninsubria.derma_bsa.R
import com.uninsubria.derma_bsa.databinding.FragmentWelcomeBinding

/**
 * Schermata iniziale dell'applicazione.
 *
 * Mostra il nome dell'app e l'icona. Il pulsante "Misura" porta alla
 * schermata di selezione del paziente ([PatientListFragment]).
 */
class WelcomeFragment : Fragment() {

    private var _binding: FragmentWelcomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWelcomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnMisura.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, PatientListFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
