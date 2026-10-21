package com.consorciotransparente.app.ui.proveedores

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.consorciotransparente.app.data.model.Provider
import com.consorciotransparente.app.databinding.ItemProviderBinding

class ProviderAdapter(private val onClick: (Provider) -> Unit) :
    RecyclerView.Adapter<ProviderAdapter.ProviderViewHolder>() {

    private var items: List<Provider> = emptyList()

    fun submitList(newItems: List<Provider>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProviderViewHolder {
        val binding = ItemProviderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProviderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProviderViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class ProviderViewHolder(private val binding: ItemProviderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(provider: Provider) {
            binding.textName.text = provider.name
            binding.textCategory.text = "${provider.category} · ${provider.phone}"
            binding.root.setOnClickListener { onClick(provider) }
        }
    }
}
