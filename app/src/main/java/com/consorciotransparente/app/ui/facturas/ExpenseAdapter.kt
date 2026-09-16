package com.consorciotransparente.app.ui.facturas

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.consorciotransparente.app.R
import com.consorciotransparente.app.data.model.Expense
import com.consorciotransparente.app.databinding.ItemExpenseBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class ExpenseAdapter(private var items: List<Expense> = emptyList()) :
    RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder>() {

    private val esAr = Locale.Builder().setLanguage("es").setRegion("AR").build()
    private val currencyFormat = NumberFormat.getCurrencyInstance(esAr)
    private val dateFormat = SimpleDateFormat("dd/MM", esAr)

    fun submitList(newItems: List<Expense>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val binding = ItemExpenseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ExpenseViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class ExpenseViewHolder(private val binding: ItemExpenseBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(expense: Expense) {
            val context = binding.root.context
            binding.textDescription.text = expense.description
            val dateText = expense.createdAt?.let { dateFormat.format(it) } ?: ""
            binding.textProviderDate.text = listOf(expense.providerName, dateText)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            binding.textAmount.text = currencyFormat.format(expense.amount)

            if (expense.isVerified) {
                binding.textProofStatus.text = "📷 Foto factura disponible"
                binding.textProofStatus.setTextColor(context.getColor(R.color.tag_green_fg))
                binding.textStatusTag.text = "Verificado"
                binding.textStatusTag.background.setTint(context.getColor(R.color.tag_green_bg))
                binding.textStatusTag.setTextColor(context.getColor(R.color.tag_green_fg))
            } else {
                binding.textProofStatus.text = "⚠️ Sin comprobante — bloqueado"
                binding.textProofStatus.setTextColor(context.getColor(R.color.tag_red_fg))
                binding.textStatusTag.text = "Bloqueado"
                binding.textStatusTag.background.setTint(context.getColor(R.color.tag_red_bg))
                binding.textStatusTag.setTextColor(context.getColor(R.color.tag_red_fg))
            }
        }
    }
}
