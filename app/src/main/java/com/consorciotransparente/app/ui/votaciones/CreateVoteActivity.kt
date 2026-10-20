package com.consorciotransparente.app.ui.votaciones

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import com.consorciotransparente.app.ui.BaseActivity
import androidx.lifecycle.ViewModelProvider
import com.consorciotransparente.app.databinding.ActivityCreateVoteBinding
import com.consorciotransparente.app.util.Resource
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** HU-02 Escenario 4: sólo el Administrador ve esta pantalla (el FAB de la lista se oculta para los demás roles). */
class CreateVoteActivity : BaseActivity() {

    companion object {
        const val EXTRA_BUILDING_ID = "buildingId"
        private const val MAX_OPTIONS = 6
    }

    private lateinit var binding: ActivityCreateVoteBinding
    private val viewModel: CreateVoteViewModel by lazy {
        ViewModelProvider(this)[CreateVoteViewModel::class.java]
    }
    private val optionInputs = mutableListOf<EditText>()
    private var selectedDate: Calendar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateVoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonBack.setOnClickListener { finish() }

        addOptionInput()
        addOptionInput()

        binding.textAddOption.setOnClickListener {
            if (optionInputs.size < MAX_OPTIONS) addOptionInput()
        }

        binding.buttonPickDate.setOnClickListener { showDatePicker() }

        binding.buttonCreate.setOnClickListener {
            val buildingId = intent.getStringExtra(EXTRA_BUILDING_ID).orEmpty()
            viewModel.create(
                buildingId = buildingId,
                title = binding.inputTitle.text?.toString().orEmpty(),
                description = binding.inputDescription.text?.toString().orEmpty(),
                options = optionInputs.map { it.text?.toString().orEmpty() },
                closesAt = selectedDate?.time
            )
        }

        viewModel.state.observe(this) { state ->
            binding.progressBar.visibility = if (state is Resource.Loading) View.VISIBLE else View.GONE
            binding.buttonCreate.isEnabled = state !is Resource.Loading
            when (state) {
                is Resource.Error -> {
                    binding.textError.text = state.message
                    binding.textError.visibility = View.VISIBLE
                }
                is Resource.Success -> finish()
                else -> Unit
            }
        }
    }

    private fun addOptionInput() {
        val index = optionInputs.size + 1
        val editText = EditText(this).apply {
            hint = getString(com.consorciotransparente.app.R.string.vote_option_hint, index)
        }
        optionInputs.add(editText)
        binding.optionsContainer.addView(editText)
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val chosen = Calendar.getInstance().apply {
                    set(year, month, day, 23, 59, 59)
                }
                selectedDate = chosen
                val format = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                binding.textSelectedDate.text = "Cierra el ${format.format(chosen.time)} a las 23:59"
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = System.currentTimeMillis()
        }.show()
    }
}
