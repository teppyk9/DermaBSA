package com.uninsubria.derma_bsa

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.commit
import com.uninsubria.derma_bsa.databinding.ActivityMainBinding
import com.uninsubria.derma_bsa.ui.fragment.WelcomeFragment

/**
 * Activity principale e unico contenitore dell'applicazione.
 *
 * Ospita un [android.widget.FrameLayout] in cui vengono sostituiti i Fragment
 * durante la navigazione. Non contiene logica di business.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.fragment_container, WelcomeFragment())
            }
        }
    }
}
