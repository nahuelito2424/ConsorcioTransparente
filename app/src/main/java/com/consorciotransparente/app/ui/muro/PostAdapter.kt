package com.consorciotransparente.app.ui.muro

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.consorciotransparente.app.data.model.Post
import com.consorciotransparente.app.databinding.ItemPostBinding
import java.text.SimpleDateFormat
import java.util.Locale

class PostAdapter : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    private var items: List<Post> = emptyList()
    private val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())

    fun submitList(newItems: List<Post>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class PostViewHolder(private val binding: ItemPostBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(post: Post) {
            binding.textPostTitle.text = post.title
            binding.textPostBody.text = post.body
            val dateText = post.createdAt?.let { dateFormat.format(it) }.orEmpty()
            binding.textPostMeta.text = listOf(post.authorName, dateText).filter { it.isNotBlank() }.joinToString(" · ")

            if (post.imageUrl.isNotBlank()) {
                binding.imagePost.visibility = View.VISIBLE
                Glide.with(binding.imagePost).load(post.imageUrl).centerCrop().into(binding.imagePost)
            } else {
                binding.imagePost.visibility = View.GONE
            }
        }
    }
}
