package com.consorciotransparente.app.ui.building

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.consorciotransparente.app.ui.BaseActivity
import androidx.lifecycle.ViewModelProvider
import com.consorciotransparente.app.R
import com.consorciotransparente.app.data.model.Building
import com.consorciotransparente.app.databinding.ActivityBuildingConfigBinding
import com.consorciotransparente.app.util.Resource

/**
 * HU-09 (Configuración de Cobros y Vencimientos). También cumple el rol de alta de
 * edificio: si el usuario todavía no tiene buildingId, esta misma pantalla lo crea
 * y lo promueve a Administrador (ver BuildingRepository.createBuilding). Una vez
 * creado, muestra su id como "código para invitar vecinos" (ver
 * AuthRepository.register): no hay un código corto propio, se comparte el id tal cual.
 */
class BuildingConfigActivity : BaseActivity() {

    private lateinit var binding: ActivityBuildingConfigBinding
    private val viewModel: BuildingConfigViewModel by lazy {
        ViewModelProvider(this)[BuildingConfigViewModel::class.java]
    }
    private var isNew = true
    private var existingBuildingId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBuildingConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel.loadState.observe(this) { state ->
            binding.progressBar.visibility = if (state is Resource.Loading) View.VISIBLE else View.GONE
            when (state) {
                is Resource.Success -> renderLoaded(state.data)
                is Resource.Error -> {
                    binding.textError.text = state.message
                    binding.textError.visibility = View.VISIBLE
                }
                else -> Unit
            }
        }

        viewModel.saveState.observe(this) { state ->
            binding.progressBar.visibility = if (state is Resource.Loading) View.VISIBLE else View.GONE
            binding.buttonSave.isEnabled = state !is Resource.Loading
            when (state) {
                is Resource.Error -> {
                    binding.textError.text = state.message
                    binding.textError.visibility = View.VISIBLE
                }
                is Resource.Success -> finish()
                else -> Unit
            }
        }

        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonSave.setOnClickListener { onSaveClicked() }
        binding.buttonCopyInviteCode.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Código del edificio", existingBuildingId))
            Toast.makeText(this, R.string.pagar_copied, Toast.LENGTH_SHORT).show()
        }

        viewModel.load()
    }

    private fun renderLoaded(data: BuildingConfigLoadState) {
        isNew = data.isNew
        val building = data.building ?: Building()
        existingBuildingId = building.id

        binding.textTitle.text = getString(
            if (isNew) R.string.building_config_title_new else R.string.building_config_title_edit
        )
        binding.buttonSave.text = getString(
            if (isNew) R.string.building_save_new else R.string.building_save_edit
        )

        // El código recién existe una vez creado el edificio (su id es el propio código).
        binding.sectionInviteCode.visibility = if (isNew) View.GONE else View.VISIBLE
        binding.textInviteCode.text = getString(R.string.building_invite_code, existingBuildingId)

        binding.inputName.setText(building.name)
        binding.inputAddress.setText(building.address)
        binding.inputCbu.setText(building.cbu)
        binding.inputAlias.setText(building.alias)
        binding.switchChargesRent.isChecked = building.chargesRent
        binding.switchChargesExpenses.isChecked = building.chargesExpenses
        binding.inputRentDueDay.setText(building.rentDueDay.toString())
        binding.inputExpensesDueDay.setText(building.expensesDueDay.toString())
        binding.inputReminderDays.setText(building.reminderDaysBefore.toString())
        binding.switchHasEncargado.isChecked = building.hasEncargado
        binding.inputAmenities.setText(building.amenities.joinToString(", "))
    }

    private fun onSaveClicked() {
        val building = Building(
            id = existingBuildingId,
            name = binding.inputName.text?.toString()?.trim().orEmpty(),
            address = binding.inputAddress.text?.toString()?.trim().orEmpty(),
            cbu = binding.inputCbu.text?.toString()?.trim().orEmpty(),
            alias = binding.inputAlias.text?.toString()?.trim().orEmpty(),
            chargesRent = binding.switchChargesRent.isChecked,
            chargesExpenses = binding.switchChargesExpenses.isChecked,
            rentDueDay = binding.inputRentDueDay.text?.toString()?.toIntOrNull()?.coerceIn(1, 28) ?: 10,
            expensesDueDay = binding.inputExpensesDueDay.text?.toString()?.toIntOrNull()?.coerceIn(1, 28) ?: 10,
            reminderDaysBefore = binding.inputReminderDays.text?.toString()?.toIntOrNull()?.coerceAtLeast(0) ?: 5,
            hasEncargado = binding.switchHasEncargado.isChecked,
            amenities = binding.inputAmenities.text?.toString()
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        )

        if (building.name.isBlank()) {
            binding.textError.text = "Ponele un nombre al edificio."
            binding.textError.visibility = View.VISIBLE
            return
        }

        viewModel.save(building, isNew)
    }
}
