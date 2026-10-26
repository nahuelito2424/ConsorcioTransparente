package com.consorciotransparente.app.ui.rendicion

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.consorciotransparente.app.R
import com.consorciotransparente.app.data.model.Expense
import com.consorciotransparente.app.data.model.ExpenseCategory
import com.consorciotransparente.app.databinding.ItemRendicionCategoriaBinding
import com.consorciotransparente.app.databinding.ItemRendicionGastoBinding
import java.text.NumberFormat
import java.util.Locale

sealed class RendicionRow {
    data class Categoria(val category: ExpenseCategory, val label: String, val total: Double, val expanded: Boolean) :
        RendicionRow()
    data class Gasto(val expense: Expense) : RendicionRow()
}

private const val VIEW_TYPE_CATEGORIA = 0
private const val VIEW_TYPE_GASTO = 1

class RendicionCuentasAdapter(
    private val onToggle: (ExpenseCategory) -> Unit,
    private val onVerComprobante: (Expense) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<RendicionRow> = emptyList()
    private val esAr = Locale.Builder().setLanguage("es").setRegion("AR").build()
    private val currencyFormat = NumberFormat.getCurrencyInstance(esAr)

    fun submitList(newItems: List<RendicionRow>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is RendicionRow.Categoria -> VIEW_TYPE_CATEGORIA
        is RendicionRow.Gasto -> VIEW_TYPE_GASTO
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_CATEGORIA) {
            CategoriaViewHolder(ItemRendicionCategoriaBinding.inflate(inflater, parent, false))
        } else {
            GastoViewHolder(ItemRendicionGastoBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = items[position]) {
            is RendicionRow.Categoria -> (holder as CategoriaViewHolder).bind(row)
            is RendicionRow.Gasto -> (holder as GastoViewHolder).bind(row.expense)
        }
    }

    inner class CategoriaViewHolder(private val binding: ItemRendicionCategoriaBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(row: RendicionRow.Categoria) {
            binding.textLabel.text = row.label
            binding.textTotal.text = currencyFormat.format(row.total)
            binding.iconExpand.rotation = if (row.expanded) 90f else 0f
            binding.root.setOnClickListener { onToggle(row.category) }
        }
    }

    inner class GastoViewHolder(private val binding: ItemRendicionGastoBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(expense: Expense) {
            val context = binding.root.context
            binding.textDescription.text = expense.description
            val provider = expense.providerName.ifBlank { context.getString(R.string.rendicion_sin_proveedor) }
            binding.textProviderAmount.text = "$provider · ${currencyFormat.format(expense.amount)}"
            binding.buttonVerComprobante.setOnClickListener { onVerComprobante(expense) }
        }
    }
}
