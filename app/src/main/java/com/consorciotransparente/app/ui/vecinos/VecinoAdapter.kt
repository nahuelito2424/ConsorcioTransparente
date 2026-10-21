package com.consorciotransparente.app.ui.vecinos

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
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

        binding.inputRent.setText(if (row.user.rentAmount > 0.0) row.user.rentAmount.toString() else "")
        binding.inputExpenses.setText(if (row.user.expensesAmount > 0.0) row.user.expensesAmount.toString() else "")

        binding.buttonSave.setOnClickListener {
            val rentAmount = binding.inputRent.text?.toString()?.toDoubleOrNull() ?: 0.0
            val expensesAmount = binding.inputExpenses.text?.toString()?.toDoubleOrNull() ?: 0.0
            onSave(row.user.uid, rentAmount, expensesAmount)
        }
    }
}
