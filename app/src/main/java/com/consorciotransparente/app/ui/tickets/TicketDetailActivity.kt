package com.consorciotransparente.app.ui.tickets

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import com.consorciotransparente.app.ui.BaseActivity
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.consorciotransparente.app.R
import com.consorciotransparente.app.data.model.Ticket
import com.consorciotransparente.app.data.model.TicketStatus
import com.consorciotransparente.app.databinding.ActivityTicketDetailBinding
import com.consorciotransparente.app.util.Resource
import java.text.NumberFormat
import java.util.Locale

/** HU-10: una sola pantalla que muestra las acciones que corresponden según el estado del ticket. */
class TicketDetailActivity : BaseActivity() {

    companion object {
        const val EXTRA_BUILDING_ID = "buildingId"
        const val EXTRA_TICKET_ID = "ticketId"
    }

    private lateinit var binding: ActivityTicketDetailBinding
    private val viewModel: TicketDetailViewModel by lazy {
        ViewModelProvider(this)[TicketDetailViewModel::class.java]
    }
    private lateinit var buildingId: String
    private lateinit var ticketId: String
    private var purchaseReceiptUri: Uri? = null
    private var paymentReceiptUri: Uri? = null
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("es").setRegion("AR").build())

    private val pickPurchaseReceipt = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            purchaseReceiptUri = uri
            binding.imagePurchasePreview.setImageURI(uri)
            binding.imagePurchasePreview.visibility = View.VISIBLE
        }
    }

    private val pickPaymentReceipt = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            paymentReceiptUri = uri
            confirmMarkPaid(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTicketDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        buildingId = intent.getStringExtra(EXTRA_BUILDING_ID).orEmpty()
        ticketId = intent.getStringExtra(EXTRA_TICKET_ID).orEmpty()

        binding.buttonBack.setOnClickListener { finish() }

        binding.buttonSaveBudget.setOnClickListener {
            val amount = binding.inputBudget.text?.toString()?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
            viewModel.saveBudget(buildingId, ticketId, amount)
        }
        binding.buttonAttachPurchaseReceipt.setOnClickListener { pickPurchaseReceipt.launch("image/*") }
        binding.buttonConfirmPurchase.setOnClickListener {
            val receipt = purchaseReceiptUri
            if (receipt == null) {
                showError("Adjuntá el comprobante de compra antes de confirmar.")
                return@setOnClickListener
            }
            val amount = binding.inputRealAmount.text?.toString()?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
            if (amount <= 0.0) {
                showError("Ingresá el monto real de la compra.")
                return@setOnClickListener
            }
            viewModel.confirmPurchase(buildingId, ticketId, amount, receipt)
        }
        binding.buttonMarkPaid.setOnClickListener { pickPaymentReceipt.launch("image/*") }
        binding.buttonPayMercadoPago.setOnClickListener { viewModel.payWithMercadoPago(buildingId, ticketId) }

        viewModel.state.observe(this) { state ->
            binding.progressBar.visibility = if (state is Resource.Loading) View.VISIBLE else View.GONE
            when (state) {
                is Resource.Success -> render(state.data)
                is Resource.Error -> showError(state.message)
                else -> Unit
            }
        }
        viewModel.actionState.observe(this) { state ->
            if (state is Resource.Error) showError(state.message)
        }
        viewModel.mercadoPagoUrl.observe(this) { state ->
            binding.buttonPayMercadoPago.isEnabled = state !is Resource.Loading
            when (state) {
                is Resource.Success -> if (state.data.isNotBlank()) {
                    CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(state.data))
                }
                is Resource.Error -> showError(state.message)
                else -> Unit
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.load(buildingId, ticketId)
    }

    private fun confirmMarkPaid(receipt: Uri) {
        viewModel.markPaid(buildingId, ticketId, receipt)
    }

    private fun render(data: TicketDetailUiState) {
        val ticket = data.ticket
        binding.textCategory.text = ticket.category.ifBlank { "Otro" }
        binding.textDescription.text = ticket.description

        if (ticket.photoUrl.isNotBlank()) {
            binding.imageProblem.visibility = View.VISIBLE
            Glide.with(this).load(ticket.photoUrl).centerCrop().into(binding.imageProblem)
        }

        val (label, bg, fg) = when (ticket.status) {
            TicketStatus.REPORTADO.name -> Triple(R.string.ticket_status_reportado, R.color.tag_blue_bg, R.color.tag_blue_fg)
            TicketStatus.EN_PRESUPUESTO.name -> Triple(R.string.ticket_status_en_presupuesto, R.color.tag_yellow_bg, R.color.tag_yellow_fg)
            TicketStatus.A_COBRAR.name -> Triple(R.string.ticket_status_a_cobrar, R.color.tag_orange_bg, R.color.tag_orange_fg)
            else -> Triple(R.string.ticket_status_resuelto, R.color.tag_green_bg, R.color.tag_green_fg)
        }
        binding.textStatusTag.setText(label)
        binding.textStatusTag.background.setTint(getColor(bg))
        binding.textStatusTag.setTextColor(getColor(fg))

        // Reset: cada estado prende sólo las secciones que le corresponden.
        binding.budgetSection.visibility = View.GONE
        binding.textBudgetInfo.visibility = View.GONE
        binding.purchaseSection.visibility = View.GONE
        binding.imagePurchaseReceipt.visibility = View.GONE
        binding.paymentSection.visibility = View.GONE
        binding.buttonPayMercadoPago.visibility = View.GONE
        binding.resolvedSection.visibility = View.GONE
        binding.textPaidByMercadoPago.visibility = View.GONE

        val isReporter = ticket.reportedByUid == data.uid

        when (ticket.status) {
            TicketStatus.REPORTADO.name -> {
                binding.budgetSection.visibility = if (data.canManage) View.VISIBLE else View.GONE
            }
            TicketStatus.EN_PRESUPUESTO.name -> {
                binding.textBudgetInfo.visibility = View.VISIBLE
                binding.textBudgetInfo.text = budgetText(ticket)
                binding.purchaseSection.visibility = if (data.canManage) View.VISIBLE else View.GONE
            }
            TicketStatus.A_COBRAR.name -> {
                binding.textBudgetInfo.visibility = View.VISIBLE
                binding.textBudgetInfo.text = budgetText(ticket)
                if (ticket.purchaseReceiptUrl.isNotBlank()) {
                    binding.imagePurchaseReceipt.visibility = View.VISIBLE
                    Glide.with(this).load(ticket.purchaseReceiptUrl).centerCrop().into(binding.imagePurchaseReceipt)
                }
                binding.paymentSection.visibility = if (data.canManage || isReporter) View.VISIBLE else View.GONE
                binding.buttonPayMercadoPago.visibility = if (isReporter) View.VISIBLE else View.GONE
                binding.textAmountToPay.text = getString(R.string.ticket_amount_to_pay, currencyFormat.format(ticket.budgetAmount))
            }
            TicketStatus.RESUELTO.name -> {
                binding.textBudgetInfo.visibility = View.VISIBLE
                binding.textBudgetInfo.text = budgetText(ticket)
                if (ticket.purchaseReceiptUrl.isNotBlank()) {
                    binding.imagePurchaseReceipt.visibility = View.VISIBLE
                    Glide.with(this).load(ticket.purchaseReceiptUrl).centerCrop().into(binding.imagePurchaseReceipt)
                }
                binding.resolvedSection.visibility = View.VISIBLE
                if (ticket.mercadoPagoPaymentId.isNotBlank()) {
                    binding.textPaidByMercadoPago.visibility = View.VISIBLE
                    binding.imagePaymentReceipt.visibility = View.GONE
                } else if (ticket.paymentReceiptUrl.isNotBlank()) {
                    binding.imagePaymentReceipt.visibility = View.VISIBLE
                    Glide.with(this).load(ticket.paymentReceiptUrl).centerCrop().into(binding.imagePaymentReceipt)
                }
            }
        }
    }

    private fun budgetText(ticket: Ticket): String {
        val amountText = if (ticket.budgetAmount <= 0.0) getString(R.string.ticket_to_quote) else currencyFormat.format(ticket.budgetAmount)
        return "Presupuesto: $amountText"
    }

    private fun showError(message: String) {
        binding.textError.text = message
        binding.textError.visibility = View.VISIBLE
    }
}
