package com.consorciotransparente.app

import android.os.Bundle
import com.consorciotransparente.app.ui.BaseActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.consorciotransparente.app.databinding.ActivityMainBinding

/** Contenedor con la barra inferior: Inicio / Facturas / Votar / Muro / Más (ver nav_graph.xml). */
class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        binding.bottomNav.setupWithNavController(navHostFragment.navController)
    }
}
