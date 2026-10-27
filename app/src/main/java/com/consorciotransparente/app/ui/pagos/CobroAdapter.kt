package com.consorciotransparente.app.ui.pagos

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.consorciotransparente.app.R
import com.consorciotransparente.app.data.model.PaymentMethod
import com.consorciotransparente.app.data.model.PaymentStatus
import com.consorciotransparente.app.databinding.ItemCobroBinding

class CobroAdapter(
    private val onRowClick: (CobroRow) -> Unit,
    private val onVerHistorial: (CobroRow) -> Unit
) : ListAdapter<CobroRow, CobroAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CobroRow>() {
            override fun areItemsTheSame(oldItem: CobroRow, newItem: CobroRow) = oldItem.user.uid == newItem.user.uid
            override fun areContentsTheSame(oldItem: CobroRow, newItem: CobroRow) = oldItem == newItem
        }
    }

    inner class ViewHolder(val binding: ItemCobroBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCobroBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = getItem(position)
        val binding = holder.binding
        val context = binding.root.context

        binding.textName.text = row.user.fullName

        val isPendingTransfer = row.payment?.status == PaymentStatus.A_VERIFICAR.name &&
            row.payment.method == PaymentMethod.TRANSFERENCIA.name
        val isPendingMercadoPago = row.payment?.status == PaymentStatus.A_VERIFICAR.name &&
            row.payment.method == PaymentMethod.MERCADO_PAGO.name

        val (label, bg, fg) = when {
            row.payment?.status == PaymentStatus.PAGADO.name ->
                Triple(R.string.cobros_status_paid, R.color.tag_green_bg, R.color.tag_green_fg)
            isPendingMercadoPago ->
                Triple(R.string.cobros_status_mercado_pago_pending, R.color.tag_yellow_bg, R.color.tag_yellow_fg)
            isPendingTransfer ->
                Triple(R.string.cobros_status_reviewing, R.color.tag_yellow_bg, R.color.tag_yellow_fg)
            else -> Triple(R.string.cobros_status_pending, R.color.tag_red_bg, R.color.tag_red_fg)
        }
        binding.textStatus.setText(label)
        binding.textStatus.background.setTint(context.getColor(bg))
        binding.textStatus.setTextColor(context.getColor(fg))

        binding.root.isClickable = isPendingTransfer
        binding.root.setOnClickListener { if (isPendingTransfer) onRowClick(row) }

        binding.textVerHistorial.paintFlags = binding.textVerHistorial.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
        binding.textVerHistorial.setOnClickListener { onVerHistorial(row) }
    }
}
