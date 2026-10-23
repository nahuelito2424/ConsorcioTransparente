package com.consorciotransparente.app.ui.vecinos

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.consorciotransparente.app.R
import com.consorciotransparente.app.databinding.ItemVecinoBinding

class VecinoAdapter(
    private val onSave: (uid: String, rentAmount: Double, expensesAmount: Double) -> Unit
) : ListAdapter<VecinoRow, VecinoAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<VecinoRow>() {
            override fun areItemsTheSame(oldItem: VecinoRow, newItem: VecinoRow) = oldItem.user.uid == newItem.user.uid
            override fun areContentsTheSame(oldItem: VecinoRow, newItem: VecinoRow) = oldItem == newItem
        }
    }

    inner class ViewHolder(val binding: ItemVecinoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVecinoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = getItem(position)
        val binding = holder.binding

        binding.textName.text = row.user.fullName
        binding.textEmail.text = row.user.email

        binding.layoutRent.visibility = if (row.showRent) View.VISIBLE else View.GONE
        binding.layoutExpenses.visibility = if (row.showExpenses) View.VISIBLE else View.GONE

        binding.inputRent.setText(formatAmountInput(row.user.rentAmount))
        binding.inputExpenses.setText(formatAmountInput(row.user.expensesAmount))

        if (row.liquidacionPeriod != null) {
            binding.textLiquidacionHint.visibility = View.VISIBLE
            binding.textLiquidacionHint.text = binding.root.context.getString(
                R.string.vecinos_liquidacion_hint,
                row.liquidacionPeriod
            )
        } else {
            binding.textLiquidacionHint.visibility = View.GONE
        }

        binding.buttonSave.setOnClickListener {
            // Mismo criterio que AddExpenseActivity/TicketDetailActivity: acepta "," como
            // separador decimal (es-AR) antes de parsear.
            val rentAmount = binding.inputRent.text?.toString()?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
            val expensesAmount = binding.inputExpenses.text?.toString()?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
            onSave(row.user.uid, rentAmount, expensesAmount)
        }
    }

    // Evita notación científica ("1.5E7") y el ".0" final que deja Double.toString().
    private fun formatAmountInput(amount: Double): String {
        if (amount <= 0.0) return ""
        return java.math.BigDecimal(amount.toString()).stripTrailingZeros().toPlainString()
    }
}
