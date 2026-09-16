package com.consorciotransparente.app.ui.tickets

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.consorciotransparente.app.R
import com.consorciotransparente.app.data.model.Ticket
import com.consorciotransparente.app.data.model.TicketStatus
import com.consorciotransparente.app.databinding.ItemTicketBinding

class TicketAdapter(private val onClick: (Ticket) -> Unit) : RecyclerView.Adapter<TicketAdapter.TicketViewHolder>() {

    private var items: List<Ticket> = emptyList()

    fun submitList(newItems: List<Ticket>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TicketViewHolder {
        val binding = ItemTicketBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TicketViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TicketViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class TicketViewHolder(private val binding: ItemTicketBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(ticket: Ticket) {
            val context = binding.root.context
            binding.textCategory.text = ticket.category.ifBlank { "Otro" }
            binding.textDescription.text = ticket.description
            binding.root.setOnClickListener { onClick(ticket) }

            val (label, bg, fg) = when (ticket.status) {
                TicketStatus.REPORTADO.name -> Triple(R.string.ticket_status_reportado, R.color.tag_blue_bg, R.color.tag_blue_fg)
                TicketStatus.EN_PRESUPUESTO.name -> Triple(R.string.ticket_status_en_presupuesto, R.color.tag_yellow_bg, R.color.tag_yellow_fg)
                TicketStatus.A_COBRAR.name -> Triple(R.string.ticket_status_a_cobrar, R.color.tag_orange_bg, R.color.tag_orange_fg)
                else -> Triple(R.string.ticket_status_resuelto, R.color.tag_green_bg, R.color.tag_green_fg)
            }
            binding.textStatusTag.setText(label)
            binding.textStatusTag.background.setTint(context.getColor(bg))
            binding.textStatusTag.setTextColor(context.getColor(fg))
        }
    }
}
