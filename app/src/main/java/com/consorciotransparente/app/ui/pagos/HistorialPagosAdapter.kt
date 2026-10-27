package com.consorciotransparente.app.ui.pagos

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.consorciotransparente.app.R
import com.consorciotransparente.app.data.model.Payment
import com.consorciotransparente.app.data.model.PaymentMethod
import com.consorciotransparente.app.data.model.PaymentStatus
import com.consorciotransparente.app.databinding.ItemHistorialPagoBinding
import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HistorialPagosAdapter(
    private val onVerComprobante: (Payment) -> Unit
) : RecyclerView.Adapter<HistorialPagosAdapter.ViewHolder>() {

    private var items: List<HistorialRow> = emptyList()
    private val esAr = Locale.Builder().setLanguage("es").setRegion("AR").build()
    private val currencyFormat = NumberFormat.getCurrencyInstance(esAr)
    private val monthLabelFormat = SimpleDateFormat("MMMM yyyy", esAr)
    private val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, esAr)

    fun submitList(newItems: List<HistorialRow>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistorialPagoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    inner class ViewHolder(private val binding: ItemHistorialPagoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(row: HistorialRow) {
            val context = binding.root.context

            val (year, month) = row.period.split("-").map { it.toInt() }
            val date = Calendar.getInstance().apply { set(year, month - 1, 1) }.time
            binding.textPeriod.text = monthLabelFormat.format(date).replaceFirstChar { it.uppercase() }

            val payment = row.payment
            if (payment == null) {
                binding.textStatus.setText(R.string.historial_status_missing)
                binding.textStatus.background.setTint(context.getColor(R.color.surface_secondary))
                binding.textStatus.setTextColor(context.getColor(R.color.text_secondary))
                binding.textAmountMethod.visibility = View.GONE
                binding.textDate.visibility = View.GONE
                binding.textMotivo.visibility = View.GONE
                binding.buttonVerComprobante.visibility = View.GONE
                return
            }

            val (label, bg, fg) = when (payment.status) {
                PaymentStatus.PAGADO.name ->
                    Triple(R.string.historial_status_paid, R.color.tag_green_bg, R.color.tag_green_fg)
                PaymentStatus.A_VERIFICAR.name ->
                    Triple(R.string.historial_status_reviewing, R.color.tag_yellow_bg, R.color.tag_yellow_fg)
                else ->
                    Triple(R.string.historial_status_rejected, R.color.tag_red_bg, R.color.tag_red_fg)
            }
            binding.textStatus.setText(label)
            binding.textStatus.background.setTint(context.getColor(bg))
            binding.textStatus.setTextColor(context.getColor(fg))

            val methodLabel = if (payment.method == PaymentMethod.MERCADO_PAGO.name) {
                context.getString(R.string.pagar_method_mercado_pago)
            } else {
                context.getString(R.string.pagar_method_transferencia)
            }
            binding.textAmountMethod.text = "${currencyFormat.format(payment.amount)} · $methodLabel"
            binding.textAmountMethod.visibility = View.VISIBLE

            // rejectPayment() nunca setea verifiedAt (sólo approvePayment lo hace) — un
            // RECHAZADO muestra la fecha en que se cargó el comprobante, no una de
            // "verificación" que nunca existió.
            when {
                payment.status == PaymentStatus.PAGADO.name && payment.verifiedAt != null -> {
                    binding.textDate.text =
                        context.getString(R.string.historial_fecha_pagado, dateFormat.format(payment.verifiedAt))
                    binding.textDate.visibility = View.VISIBLE
                }
                payment.createdAt != null -> {
                    binding.textDate.text =
                        context.getString(R.string.historial_fecha_cargado, dateFormat.format(payment.createdAt))
                    binding.textDate.visibility = View.VISIBLE
                }
                else -> binding.textDate.visibility = View.GONE
            }

            if (payment.status == PaymentStatus.RECHAZADO.name && payment.rejectionReason.isNotBlank()) {
                binding.textMotivo.text = context.getString(R.string.historial_motivo_rechazo, payment.rejectionReason)
                binding.textMotivo.visibility = View.VISIBLE
            } else {
                binding.textMotivo.visibility = View.GONE
            }

            if (payment.receiptUrl.isNotBlank()) {
                binding.buttonVerComprobante.visibility = View.VISIBLE
                binding.buttonVerComprobante.setOnClickListener { onVerComprobante(payment) }
            } else {
                binding.buttonVerComprobante.visibility = View.GONE
            }
        }
    }
}
