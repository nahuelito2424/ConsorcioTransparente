package com.consorciotransparente.app.ui.rendicion

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.consorciotransparente.app.R
import com.consorciotransparente.app.data.model.Expense
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
    private var expandedCategories = setOf<ExpenseCategory>()
    private var lastState: RendicionUiState? = null

    private val adapter = RendicionCuentasAdapter(
        onToggle = { category ->
            expandedCategories = if (category in expandedCategories) {
                expandedCategories - category
            } else {
                expandedCategories + category
            }
            lastState?.let { submitRows(it) }
        },
        onVerComprobante = { expense -> showReceiptDialog(expense) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRendicionCuentasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerCategorias.layoutManager = LinearLayoutManager(this)
        binding.recyclerCategorias.adapter = adapter

        period = intent.getStringExtra("period") ?: currentPeriod()

        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonPrevMonth.setOnClickListener { load(shiftPeriod(period, -1)) }
        binding.buttonNextMonth.setOnClickListener { load(shiftPeriod(period, 1)) }
        binding.buttonDescargarPdf.setOnClickListener { descargarPdf() }

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
        expandedCategories = emptySet() // no tiene sentido arrastrar una categoría abierta de otro mes
        val (year, month) = period.split("-").map { it.toInt() }
        val date = Calendar.getInstance().apply { set(year, month - 1, 1) }.time
        binding.textPeriod.text = monthLabelFormat.format(date).replaceFirstChar { it.uppercase() }
        viewModel.load(period)
    }

    private fun render(data: RendicionUiState) {
        lastState = data
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

        submitRows(data)

        binding.textEmptyState.visibility =
            if (data.categoryTotals.isEmpty() && data.bloqueadoCount == 0) View.VISIBLE else View.GONE
    }

    private fun submitRows(data: RendicionUiState) {
        val rows = data.categoryTotals.flatMap { (category, total) ->
            val expanded = category in expandedCategories
            val categoriaRow = RendicionRow.Categoria(category, categoryLabel(category), total, expanded)
            if (expanded) {
                listOf(categoriaRow) + (data.expensesByCategory[category] ?: emptyList()).map { RendicionRow.Gasto(it) }
            } else {
                listOf(categoriaRow)
            }
        }
        adapter.submitList(rows)
    }

    private fun categoryLabel(category: ExpenseCategory): String {
        val labels = resources.getStringArray(R.array.expense_categories)
        val index = ExpenseCategory.entries.indexOf(category)
        return labels.getOrElse(index) { category.name }
    }

    private fun showReceiptDialog(expense: Expense) {
        val imageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(240))
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        Glide.with(this).load(expense.receiptUrl).into(imageView)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dpToPx(16)
            setPadding(padding, padding, padding, padding)
            addView(imageView)
        }

        AlertDialog.Builder(this)
            .setTitle(expense.description)
            .setView(container)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun descargarPdf() {
        val state = lastState ?: return
        val file = generarPdf(this, state.buildingName, period, state)
        val uri = FileProvider.getUriForFile(this, "com.consorciotransparente.app.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.rendicion_compartir_pdf)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.rendicion_sin_app_pdf, Toast.LENGTH_LONG).show()
        }
    }
}
