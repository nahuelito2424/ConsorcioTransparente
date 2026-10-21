package com.consorciotransparente.app.ui.pagos

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import com.consorciotransparente.app.R
import com.consorciotransparente.app.data.model.Payment
import com.consorciotransparente.app.data.model.PaymentMethod
import com.consorciotransparente.app.data.model.PaymentStatus
import com.consorciotransparente.app.databinding.ActivityPagarBinding
import com.consorciotransparente.app.ui.BaseActivity
import com.consorciotransparente.app.util.Resource
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Locale

/** HU-03: lo que debo pagar este mes, por transferencia (comprobante) o — más adelante — Mercado Pago. */
class PagarActivity : BaseActivity() {
    private lateinit var binding: ActivityPagarBinding
    private val viewModel: PagarViewModel by lazy { ViewModelProvider(this)[PagarViewModel::class.java] }
    private val spanishArgentina = Locale.Builder().setLanguage("es").setRegion("AR").build()
    private val currencyFormat = NumberFormat.getCurrencyInstance(spanishArgentina)

    private val pickReceipt = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.uploadReceipt(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPagarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonAttachReceipt.setOnClickListener { pickReceipt.launch("image/*") }
        binding.buttonCopyCbu.setOnClickListener { copyToClipboard(binding.textCbu.text.toString().substringAfter(": ")) }
        binding.buttonCopyAlias.setOnClickListener { copyToClipboard(binding.textAlias.text.toString().substringAfter(": ")) }
        binding.buttonHistory.setOnClickListener { viewModel.loadHistory() }

        viewModel.history.observe(this) { state ->
            if (state is Resource.Success) showHistory(state.data)
        }

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
        viewModel.uploadState.observe(this) { state ->
            binding.buttonAttachReceipt.isEnabled = state !is Resource.Loading
            if (state is Resource.Error) {
                binding.textError.text = state.message
                binding.textError.visibility = View.VISIBLE
            }
        }

        viewModel.load()
    }

    private fun render(data: PagoUiState) {
        binding.textError.visibility = View.GONE

        val payment = data.payment
        // Mientras hay un pago A_VERIFICAR o PAGADO, el desglose mostrado es el que quedó
        // congelado en el pago (HU-03), no el monto asignado hoy, que puede haber cambiado.
        val showFrozenAmount = payment != null && payment.status != PaymentStatus.RECHAZADO.name
        val displayBreakdown = if (showFrozenAmount) payment!!.breakdown else data.breakdown
        val displayTotal = if (showFrozenAmount) payment!!.amount else data.total

        binding.breakdownContainer.removeAllViews()
        val conceptLabels = mapOf("alquiler" to R.string.pagar_concept_rent, "expensas" to R.string.pagar_concept_expenses)
        for ((concept, amount) in displayBreakdown) {
            val row = TextView(this).apply {
                val label = conceptLabels[concept]?.let { getString(it) } ?: concept
                text = "$label: ${currencyFormat.format(amount)}"
                textSize = 13f
                setTextColor(getColor(R.color.text_primary))
            }
            binding.breakdownContainer.addView(row)
        }
        val totalRow = TextView(this).apply {
            text = "${getString(R.string.pagar_total)}: ${currencyFormat.format(displayTotal)}"
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.navy))
            setPadding(0, 12, 0, 0)
        }
        binding.breakdownContainer.addView(totalRow)

        binding.textNoAmount.visibility = View.GONE
        binding.sectionPending.visibility = View.GONE
        binding.textReviewing.visibility = View.GONE
        binding.sectionPaid.visibility = View.GONE
        binding.textRejected.visibility = View.GONE

        // El monto vigente hoy (data.total) sólo bloquea un pago nuevo: un pago que ya
        // existe (A_VERIFICAR/PAGADO) se sigue mostrando aunque el Admin haya bajado el
        // monto asignado después.
        if (payment == null && data.total <= 0.0) {
            binding.textNoAmount.visibility = View.VISIBLE
            return
        }

        when (payment?.status) {
            PaymentStatus.A_VERIFICAR.name -> {
                binding.textReviewing.visibility = View.VISIBLE
            }
            PaymentStatus.PAGADO.name -> {
                binding.sectionPaid.visibility = View.VISIBLE
                val date = payment.verifiedAt ?: payment.createdAt
                binding.textPaid.text = getString(
                    R.string.pagar_paid_on,
                    date?.let { DateFormat.getDateInstance(DateFormat.MEDIUM, spanishArgentina).format(it) } ?: payment.period,
                    currencyFormat.format(payment.amount),
                    methodLabel(payment.method)
                )
            }
            else -> {
                // null o RECHAZADO
                if (data.total <= 0.0) {
                    binding.textNoAmount.visibility = View.VISIBLE
                    return
                }
                binding.sectionPending.visibility = View.VISIBLE
                binding.textCbu.text = getString(R.string.pagar_cbu, data.cbu)
                binding.textAlias.text = getString(R.string.pagar_alias, data.alias)
                if (payment?.status == PaymentStatus.RECHAZADO.name) {
                    binding.textRejected.visibility = View.VISIBLE
                    binding.textRejected.text = getString(R.string.pagar_rejected, payment.rejectionReason)
                }
            }
        }
    }

    private fun methodLabel(method: String): String = when (method) {
        PaymentMethod.MERCADO_PAGO.name -> getString(R.string.pagar_method_mercado_pago)
        else -> getString(R.string.pagar_method_transferencia)
    }

    private fun statusLabel(status: String): String = when (status) {
        PaymentStatus.PAGADO.name -> getString(R.string.pagar_status_paid)
        PaymentStatus.A_VERIFICAR.name -> getString(R.string.pagar_status_reviewing)
        else -> getString(R.string.pagar_status_rejected)
    }

    private fun showHistory(history: List<Payment>) {
        val message = if (history.isEmpty()) {
            getString(R.string.pagar_history_empty)
        } else {
            history.joinToString("\n") {
                getString(R.string.pagar_history_row, it.period, currencyFormat.format(it.amount), statusLabel(it.status))
            }
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.pagar_history)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("CBU/alias", text))
        Toast.makeText(this, R.string.pagar_copied, Toast.LENGTH_SHORT).show()
    }
}
