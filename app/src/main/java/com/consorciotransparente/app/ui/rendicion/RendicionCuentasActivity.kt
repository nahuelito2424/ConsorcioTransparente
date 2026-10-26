package com.consorciotransparente.app.ui.rendicion

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import com.consorciotransparente.app.R
import com.consorciotransparente.app.data.model.ExpenseCategory
import com.consorciotransparente.app.databinding.ActivityRendicionCuentasBinding
import com.consorciotransparente.app.ui.BaseActivity
import com.consorciotransparente.app.util.Resource
import com.consorciotransparente.app.util.currentPeriod
import com.consorciotransparente.app.util.shiftPeriod
import java.text.NumberFormat
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale

/** Backlog "Rendición de cuentas descargable" — ver design doc en docs/superpowers/specs. */
class RendicionCuentasActivity : BaseActivity() {
    private lateinit var binding: ActivityRendicionCuentasBinding
    private val viewModel: RendicionCuentasViewModel by lazy {
        ViewModelProvider(this)[RendicionCuentasViewModel::class.java]
    }
    private val esAr = Locale.Builder().setLanguage("es").setRegion("AR").build()
    private val currencyFormat = NumberFormat.getCurrencyInstance(esAr)
    private val monthLabelFormat = SimpleDateFormat("MMMM yyyy", esAr)

    private var period = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRendicionCuentasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        period = intent.getStringExtra("period") ?: currentPeriod()

        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonPrevMonth.setOnClickListener { load(shiftPeriod(period, -1)) }
        binding.buttonNextMonth.setOnClickListener { load(shiftPeriod(period, 1)) }

        viewModel.state.observe(this) { state ->
            binding.progressBar.visibility = if (state is Resource.Loading) View.VISIBLE else View.GONE
            binding.textError.visibility = View.GONE
            when (state) {
                is Resource.Success -> render(state.data)
                is Resource.Error -> {
                    binding.textError.text = state.message
                    binding.textError.visibility = View.VISIBLE
                }
                else -> Unit
            }
        }

        load(period)
    }

    private fun load(newPeriod: String) {
        period = newPeriod
        val (year, month) = period.split("-").map { it.toInt() }
        val date = Calendar.getInstance().apply { set(year, month - 1, 1) }.time
        binding.textPeriod.text = monthLabelFormat.format(date).replaceFirstChar { it.uppercase() }
        viewModel.load(period)
    }

    private fun render(data: RendicionUiState) {
        binding.textTotal.text = currencyFormat.format(data.totalVerificado)

        if (data.bloqueadoCount > 0) {
            binding.textBloqueadoWarning.text = resources.getQuantityString(
                R.plurals.rendicion_bloqueado_warning,
                data.bloqueadoCount,
                data.bloqueadoCount,
                currencyFormat.format(data.bloqueadoTotal)
            )
            binding.textBloqueadoWarning.visibility = View.VISIBLE
        } else {
            binding.textBloqueadoWarning.visibility = View.GONE
        }

        binding.categoryContainer.removeAllViews()
        for ((category, total) in data.categoryTotals) {
            val row = TextView(this).apply {
                text = "${categoryLabel(category)}: ${currencyFormat.format(total)}"
                textSize = 14f
                setTextColor(getColor(R.color.text_primary))
                setPadding(0, 12, 0, 12)
            }
            binding.categoryContainer.addView(row)
        }

        binding.textEmptyState.visibility =
            if (data.categoryTotals.isEmpty() && data.bloqueadoCount == 0) View.VISIBLE else View.GONE
    }

    private fun categoryLabel(category: ExpenseCategory): String {
        val labels = resources.getStringArray(R.array.expense_categories)
        val index = ExpenseCategory.entries.indexOf(category)
        return labels.getOrElse(index) { category.name }
    }
}
