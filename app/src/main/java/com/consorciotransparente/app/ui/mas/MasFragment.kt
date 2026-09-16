package com.consorciotransparente.app.ui.mas

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.consorciotransparente.app.data.model.Role
import com.consorciotransparente.app.data.repository.AuthRepository
import com.consorciotransparente.app.databinding.FragmentMasBinding
import com.consorciotransparente.app.ui.auth.LoginActivity
import com.consorciotransparente.app.ui.building.BuildingConfigActivity
import com.consorciotransparente.app.ui.reservas.ReservasActivity
import com.consorciotransparente.app.ui.tickets.TicketsActivity
import kotlinx.coroutines.launch

/**
 * Menú "Más": acceso a Reservas (HU-06) y a la configuración del edificio (HU-09,
 * también sirve para crear el edificio si el usuario todavía no tiene uno asignado).
 * Proveedores/Protocolos quedan como stubs para próximas iteraciones (HU-08, HU-07).
 */
class MasFragment : Fragment() {

    private var _binding: FragmentMasBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.itemBuildingConfig.setOnClickListener {
            startActivity(Intent(requireContext(), BuildingConfigActivity::class.java))
        }
        binding.itemTickets.setOnClickListener {
            startActivity(Intent(requireContext(), TicketsActivity::class.java))
        }
        binding.itemReservas.setOnClickListener {
            startActivity(Intent(requireContext(), ReservasActivity::class.java))
        }
        binding.buttonLogout.setOnClickListener {
            AuthRepository().logout()
            startActivity(
                Intent(requireContext(), LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // La visibilidad puede cambiar (ej. justo se creó el edificio), así que se
        // recalcula cada vez que se vuelve a esta pantalla, no sólo en onViewCreated.
        viewLifecycleOwner.lifecycleScope.launch {
            val user = AuthRepository().fetchCurrentUserProfile()
            val showBuildingConfig = user != null && (user.buildingId.isBlank() || user.roleEnum() == Role.ADMINISTRADOR)
            binding.itemBuildingConfig.visibility = if (showBuildingConfig) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
