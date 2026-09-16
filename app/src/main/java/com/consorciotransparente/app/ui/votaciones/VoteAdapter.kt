package com.consorciotransparente.app.ui.votaciones

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.consorciotransparente.app.R
import com.consorciotransparente.app.databinding.ItemVoteBinding
import java.util.concurrent.TimeUnit

class VoteAdapter(
    private val onClick: (VoteListItem) -> Unit
) : RecyclerView.Adapter<VoteAdapter.VoteViewHolder>() {

    private var items: List<VoteListItem> = emptyList()

    fun submitList(newItems: List<VoteListItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VoteViewHolder {
        val binding = ItemVoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VoteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VoteViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class VoteViewHolder(private val binding: ItemVoteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: VoteListItem) {
            val context = binding.root.context
            binding.textVoteTitle.text = item.vote.title
            binding.root.setOnClickListener { onClick(item) }

            val (tagText, bg, fg) = when {
                item.isClosed -> Triple(
                    context.getString(R.string.vote_closed), R.color.tag_yellow_bg, R.color.tag_yellow_fg
                )
                item.hasVoted -> Triple(
                    context.getString(R.string.vote_already_voted), R.color.tag_green_bg, R.color.tag_green_fg
                )
                else -> Triple(
                    context.getString(R.string.vote_active), R.color.tag_blue_bg, R.color.tag_blue_fg
                )
            }
            binding.textVoteTag.text = tagText
            binding.textVoteTag.background.setTint(context.getColor(bg))
            binding.textVoteTag.setTextColor(context.getColor(fg))

            val deadlineText = item.vote.closesAt?.let { deadline ->
                if (item.isClosed) {
                    "Cerrada"
                } else {
                    val days = TimeUnit.MILLISECONDS.toDays(deadline.time - System.currentTimeMillis()).coerceAtLeast(0)
                    if (days == 0L) "Cierra hoy" else "Cierra en $days día${if (days == 1L) "" else "s"}"
                }
            } ?: "Sin fecha de cierre"

            binding.textVoteMeta.text = context.getString(R.string.vote_total_votes, item.voteCount)
                .let { "$deadlineText · $it" }
        }
    }
}
