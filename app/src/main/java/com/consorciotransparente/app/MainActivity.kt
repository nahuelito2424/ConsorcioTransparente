package com.consorciotransparente.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.consorciotransparente.app.ui.BaseActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.consorciotransparente.app.data.repository.RepositoryProvider
import com.consorciotransparente.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/** Contenedor con la barra inferior: Inicio / Facturas / Votar / Muro / Más (ver nav_graph.xml). */
class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        binding.bottomNav.setupWithNavController(navHostFragment.navController)
    }

    override fun onResume() {
        super.onResume()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Se repite en cada onResume (no sólo en onCreate) porque el usuario puede volver
        // acá recién después de crear su edificio en BuildingConfigActivity: antes de eso
        // no tenía buildingId y no había nada a qué suscribir.
        lifecycleScope.launch {
            val buildingId = RepositoryProvider.auth.fetchCurrentUserProfile()?.buildingId.orEmpty()
            RepositoryProvider.notification.subscribeToBuilding(buildingId)
        }
    }
}
