package com.consorciotransparente.app.ui.mas

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.consorciotransparente.app.data.model.Role
import com.consorciotransparente.app.data.repository.RepositoryProvider
import com.consorciotransparente.app.databinding.FragmentMasBinding
import com.consorciotransparente.app.ui.auth.LoginActivity
import com.consorciotransparente.app.ui.building.BuildingConfigActivity
import com.consorciotransparente.app.ui.pagos.CobrosActivity
import com.consorciotransparente.app.ui.pagos.PagarActivity
import com.consorciotransparente.app.ui.proveedores.ProveedoresActivity
import com.consorciotransparente.app.ui.reservas.ReservasActivity
import com.consorciotransparente.app.ui.tickets.TicketsActivity
import com.consorciotransparente.app.ui.vecinos.VecinosActivity
import com.consorciotransparente.app.util.SimpleModePrefs
import kotlinx.coroutines.launch

/**
 * Menú "Más": acceso a Reservas (HU-06), proveedores (HU-08) y a la configuración del
 * edificio (HU-09, también sirve para crear el edificio si el usuario todavía no tiene
 * uno asignado). Protocolos queda como stub para una próxima iteración (HU-07).
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
        binding.itemVecinos.setOnClickListener {
            startActivity(Intent(requireContext(), VecinosActivity::class.java))
        }
        binding.itemPagar.setOnClickListener {
            startActivity(Intent(requireContext(), PagarActivity::class.java))
        }
        binding.itemCobros.setOnClickListener {
            startActivity(Intent(requireContext(), CobrosActivity::class.java))
        }
        binding.itemProveedores.setOnClickListener {
            startActivity(Intent(requireContext(), ProveedoresActivity::class.java))
        }

        // Se fija el estado actual antes de escuchar cambios, para no disparar el
        // listener (y un recreate() innecesario) al sólo inflar la pantalla.
        binding.switchSimpleMode.isChecked = SimpleModePrefs.isEnabled(requireContext())
        binding.switchSimpleMode.setOnCheckedChangeListener { _, isChecked ->
            SimpleModePrefs.setEnabled(requireContext(), isChecked)
            requireActivity().recreate()
        }

        binding.buttonLogout.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val user = RepositoryProvider.auth.fetchCurrentUserProfile()
                val buildingId = user?.buildingId.orEmpty()
                RepositoryProvider.notification.unsubscribeFromBuilding(buildingId)
                RepositoryProvider.notification.unsubscribeFromUser(user?.uid.orEmpty())
                if (user?.roleEnum() == Role.ADMINISTRADOR || user?.roleEnum() == Role.ENCARGADO) {
                    RepositoryProvider.notification.unsubscribeFromBuildingStaff(buildingId)
                }
                RepositoryProvider.auth.logout()
                startActivity(
                    Intent(requireContext(), LoginActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // La visibilidad puede cambiar (ej. justo se creó el edificio), así que se
        // recalcula cada vez que se vuelve a esta pantalla, no sólo en onViewCreated.
        viewLifecycleOwner.lifecycleScope.launch {
            val user = RepositoryProvider.auth.fetchCurrentUserProfile()
            val showBuildingConfig = user != null && (user.buildingId.isBlank() || user.roleEnum() == Role.ADMINISTRADOR)
            binding.itemBuildingConfig.visibility = if (showBuildingConfig) View.VISIBLE else View.GONE

            val isAdmin = user?.roleEnum() == Role.ADMINISTRADOR
            binding.itemVecinos.visibility = if (isAdmin) View.VISIBLE else View.GONE
            binding.itemCobros.visibility = if (isAdmin) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
