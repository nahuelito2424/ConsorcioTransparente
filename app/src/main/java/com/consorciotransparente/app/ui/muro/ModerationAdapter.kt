package com.consorciotransparente.app.ui.muro

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.consorciotransparente.app.data.model.Post
import com.consorciotransparente.app.databinding.ItemPendingPostBinding
import java.text.SimpleDateFormat
import java.util.Locale

class ModerationAdapter(
    private val onApprove: (Post) -> Unit,
    private val onReject: (Post) -> Unit
) : RecyclerView.Adapter<ModerationAdapter.PendingViewHolder>() {

    private var items: List<Post> = emptyList()
    private val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())

    fun submitList(newItems: List<Post>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PendingViewHolder {
        val binding = ItemPendingPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PendingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PendingViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class PendingViewHolder(private val binding: ItemPendingPostBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(post: Post) {
            binding.textPostTitle.text = post.title
            binding.textPostBody.text = post.body
            val dateText = post.createdAt?.let { dateFormat.format(it) }.orEmpty()
            binding.textPostMeta.text = "Propuesto por ${post.authorName.ifBlank { "un vecino" }} · $dateText"

            if (post.imageUrl.isNotBlank()) {
                binding.imagePost.visibility = View.VISIBLE
                Glide.with(binding.imagePost).load(post.imageUrl).centerCrop().into(binding.imagePost)
            } else {
                binding.imagePost.visibility = View.GONE
            }

            binding.buttonApprove.setOnClickListener { onApprove(post) }
            binding.buttonReject.setOnClickListener { onReject(post) }
        }
    }
}
