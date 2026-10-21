package com.consorciotransparente.app.ui.proveedores

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.consorciotransparente.app.ui.BaseActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.consorciotransparente.app.R
import com.consorciotransparente.app.databinding.ActivityProviderDetailBinding
import com.consorciotransparente.app.util.Resource
import java.util.Locale

/** HU-08: ficha de un proveedor (datos, promedio de estrellas, reseñas y alta de reseña). */
class ProviderDetailActivity : BaseActivity() {

    companion object {
        const val EXTRA_PROVIDER_ID = "providerId"
    }

    private lateinit var binding: ActivityProviderDetailBinding
    private val viewModel: ProviderDetailViewModel by lazy {
        ViewModelProvider(this)[ProviderDetailViewModel::class.java]
    }
    private lateinit var providerId: String
    private val reviewAdapter = ProviderReviewAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProviderDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        providerId = intent.getStringExtra(EXTRA_PROVIDER_ID).orEmpty()

        binding.recyclerReviews.layoutManager = LinearLayoutManager(this)
        binding.recyclerReviews.adapter = reviewAdapter

        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonEdit.setOnClickListener {
            startActivity(
                Intent(this, AddEditProviderActivity::class.java)
                    .putExtra(AddEditProviderActivity.EXTRA_PROVIDER_ID, providerId)
            )
        }
        binding.buttonDelete.setOnClickListener { confirmDelete() }
        binding.buttonAddReview.setOnClickListener {
            val comment = binding.inputComment.text?.toString()?.trim().orEmpty()
            if (comment.isBlank()) {
                showError(getString(R.string.providers_review_comment_required))
                return@setOnClickListener
            }
            viewModel.addReview(binding.ratingInput.rating.toInt().coerceIn(1, 5), comment)
        }

        viewModel.state.observe(this) { state ->
            binding.progressBar.visibility = if (state is Resource.Loading) View.VISIBLE else View.GONE
            when (state) {
                is Resource.Success -> render(state.data)
                is Resource.Error -> showError(state.message)
                else -> Unit
            }
        }
        viewModel.reviewState.observe(this) { state ->
            when (state) {
                is Resource.Error -> showError(state.message)
                is Resource.Success -> binding.inputComment.setText("")
                else -> Unit
            }
        }
        viewModel.deleteState.observe(this) { state ->
            when (state) {
                is Resource.Error -> showError(state.message)
                is Resource.Success -> finish()
                else -> Unit
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Se recarga acá (no sólo en onCreate) para reflejar los cambios al volver de
        // AddEditProviderActivity (editar) sin tener que salir y reentrar a la ficha.
        viewModel.load(providerId)
    }

    private fun render(data: ProviderDetailUiState) {
        binding.textError.visibility = View.GONE
        binding.textName.text = data.provider.name
        binding.textCategoryPhone.text = "${data.provider.category} · ${data.provider.phone}"

        binding.ratingAverage.rating = data.averageStars.toFloat()
        val averageText = String.format(Locale.Builder().setLanguage("es").setRegion("AR").build(), "%.1f", data.averageStars)
        val countText = resources.getQuantityString(R.plurals.providers_review_count, data.reviews.size, data.reviews.size)
        binding.textRatingSummary.text = "$averageText ($countText)"

        binding.adminActions.visibility = if (data.isAdmin) View.VISIBLE else View.GONE

        binding.textNoReviews.visibility = if (data.reviews.isEmpty()) View.VISIBLE else View.GONE
        reviewAdapter.submitList(data.reviews)
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.providers_delete)
            .setMessage(R.string.providers_delete_confirm)
            .setPositiveButton(R.string.providers_delete) { _, _ -> viewModel.delete() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showError(message: String) {
        binding.textError.text = message
        binding.textError.visibility = View.VISIBLE
    }
}
