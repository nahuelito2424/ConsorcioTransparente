package com.consorciotransparente.app.ui.reservas

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.consorciotransparente.app.R
import com.consorciotransparente.app.data.model.Reservation
import com.consorciotransparente.app.databinding.ActivityReservasBinding
import com.consorciotransparente.app.util.Resource
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/** HU-06: disponibilidad por franja, reserva, edición y cancelación (con margen de 24hs). */
class ReservasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReservasBinding
    private val viewModel: ReservasViewModel by lazy {
        ViewModelProvider(this)[ReservasViewModel::class.java]
    }
    private var suppressSpinnerCallback = false
    private val displayDateFormat = SimpleDateFormat(
        "EEEE dd/MM/yyyy", Locale.Builder().setLanguage("es").setRegion("AR").build()
    )
    private val slotStartFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReservasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonPickDate.setOnClickListener { showDatePicker() }

        viewModel.state.observe(this) { state ->
            binding.progressBar.visibility = if (state is Resource.Loading) View.VISIBLE else View.GONE
            when (state) {
                is Resource.Success -> render(state.data)
                is Resource.Error -> {
                    binding.textError.text = state.message
                    binding.textError.visibility = View.VISIBLE
                }
                else -> Unit
            }
        }

        viewModel.start()
    }

    private fun render(data: ReservasScreenState) {
        binding.textError.visibility = View.GONE

        setupSpinner(data)

        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(data.selectedDate)
        binding.textSelectedDate.text = date?.let { displayDateFormat.format(it).replaceFirstChar(Char::uppercase) } ?: data.selectedDate

        binding.textEditingNotice.visibility = if (data.editingReservationId != null) View.VISIBLE else View.GONE
        binding.textEditingNotice.text = getString(R.string.reservas_editing_notice, data.selectedSpace)

        renderSlots(data)
        renderMyReservations(data)
    }

    private fun setupSpinner(data: ReservasScreenState) {
        if (binding.spinnerSpace.adapter == null) {
            binding.spinnerSpace.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, data.spaces)
            binding.spinnerSpace.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (suppressSpinnerCallback) return
                    viewModel.selectSpace(data.spaces[position])
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
        }
        suppressSpinnerCallback = true
        binding.spinnerSpace.setSelection(data.spaces.indexOf(data.selectedSpace).coerceAtLeast(0))
        suppressSpinnerCallback = false
    }

    private fun renderSlots(data: ReservasScreenState) {
        binding.slotsContainer.removeAllViews()
        for (slot in TIME_SLOTS) {
            val owner = data.slotOwners[slot]
            val row = layoutInflater.inflate(android.R.layout.simple_list_item_1, binding.slotsContainer, false) as TextView
            row.setPadding(16, 24, 16, 24)
            row.setBackgroundResource(R.drawable.bg_card)
            (row.layoutParams as LinearLayout.LayoutParams).bottomMargin = 8

            when {
                owner == null -> {
                    row.text = "$slot — ${getString(R.string.reservas_slot_free)}"
                    row.setTextColor(getColor(R.color.tag_green_fg))
                    row.setOnClickListener { confirmSlot(data, slot) }
                }
                owner.reservedByUid == data.uid -> {
                    row.text = "$slot — ${getString(R.string.reservas_slot_mine)}"
                    row.setTextColor(getColor(R.color.navy))
                    row.isClickable = false
                }
                else -> {
                    row.text = "$slot — ${getString(R.string.reservas_slot_taken)}"
                    row.setTextColor(getColor(R.color.tag_red_fg))
                    row.isClickable = false
                }
            }
            binding.slotsContainer.addView(row)
        }
    }

    private fun confirmSlot(data: ReservasScreenState, slot: String) {
        val message = getString(R.string.reservas_confirm_message, data.selectedSpace, binding.textSelectedDate.text, slot)
        AlertDialog.Builder(this)
            .setTitle(R.string.reservas_confirm_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.confirmSlot(slot) { error ->
                    binding.textError.text = error
                    binding.textError.visibility = View.VISIBLE
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun renderMyReservations(data: ReservasScreenState) {
        binding.myReservationsContainer.removeAllViews()
        binding.textNoReservations.visibility = if (data.myReservations.isEmpty()) View.VISIBLE else View.GONE

        for (reservation in data.myReservations) {
            binding.myReservationsContainer.addView(buildReservationRow(reservation))
        }
    }

    private fun buildReservationRow(reservation: Reservation): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 16
            }
        }

        val label = TextView(this).apply {
            text = "${reservation.spaceName} — ${reservation.date} · ${reservation.timeSlot}"
            textSize = 13f
            setTextColor(getColor(R.color.text_primary))
        }
        container.addView(label)

        val canModify = canModify(reservation)
        if (!canModify) {
            val warning = TextView(this).apply {
                text = getString(R.string.reservas_cancel_too_late)
                textSize = 11f
                setPadding(0, 8, 0, 0)
                setTextColor(getColor(R.color.tag_red_fg))
            }
            container.addView(warning)
        } else {
            val buttonsRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 12, 0, 0)
            }
            val editButton = Button(this, null, android.R.attr.buttonStyleSmall).apply {
                text = getString(R.string.reservas_edit)
                isAllCaps = false
                setOnClickListener { viewModel.startEditing(reservation) }
            }
            val cancelButton = Button(this, null, android.R.attr.buttonStyleSmall).apply {
                text = getString(R.string.reservas_cancel)
                isAllCaps = false
                setOnClickListener {
                    viewModel.cancelReservation(reservation) { error ->
                        binding.textError.text = error
                        binding.textError.visibility = View.VISIBLE
                    }
                }
            }
            buttonsRow.addView(editButton)
            buttonsRow.addView(cancelButton)
            container.addView(buttonsRow)
        }
        return container
    }

    /** Escenario 3: sólo se puede editar/cancelar con 24hs o más de anticipación. */
    private fun canModify(reservation: Reservation): Boolean {
        val startHour = reservation.timeSlot.substringBefore("-")
        val slotStart = runCatching {
            slotStartFormat.parse("${reservation.date} $startHour")
        }.getOrNull() ?: return true
        val hoursUntil = TimeUnit.MILLISECONDS.toHours(slotStart.time - System.currentTimeMillis())
        return hoursUntil >= 24
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val iso = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                viewModel.selectDate(iso)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = System.currentTimeMillis()
        }.show()
    }
}
