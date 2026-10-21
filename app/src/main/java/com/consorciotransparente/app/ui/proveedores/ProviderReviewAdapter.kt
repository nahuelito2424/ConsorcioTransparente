package com.consorciotransparente.app.ui.proveedores

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.consorciotransparente.app.data.model.ProviderReview
import com.consorciotransparente.app.databinding.ItemProviderReviewBinding

class ProviderReviewAdapter : RecyclerView.Adapter<ProviderReviewAdapter.ReviewViewHolder>() {

    private var items: List<ProviderReview> = emptyList()

    fun submitList(newItems: List<ProviderReview>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val binding = ItemProviderReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReviewViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class ReviewViewHolder(private val binding: ItemProviderReviewBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(review: ProviderReview) {
            binding.ratingStars.rating = review.stars.toFloat()
            binding.textComment.text = review.comment
        }
    }
}
