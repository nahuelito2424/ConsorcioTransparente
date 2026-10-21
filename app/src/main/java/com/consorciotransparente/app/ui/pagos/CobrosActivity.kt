package com.consorciotransparente.app.ui.pagos

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.consorciotransparente.app.R
import com.consorciotransparente.app.databinding.ActivityCobrosBinding
import com.consorciotransparente.app.ui.BaseActivity
import com.consorciotransparente.app.util.Resource

/** HU-03: el Administrador ve el estado de cobro del mes y aprueba/rechaza comprobantes. */
class CobrosActivity : BaseActivity() {
    private lateinit var binding: ActivityCobrosBinding
    private val viewModel: CobrosViewModel by lazy { ViewModelProvider(this)[CobrosViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCobrosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonBack.setOnClickListener { finish() }

        val adapter = CobroAdapter { row -> showReceiptDialog(row) }
        binding.recyclerCobros.layoutManager = LinearLayoutManager(this)
        binding.recyclerCobros.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { viewModel.load() }

        viewModel.state.observe(this) { state ->
            binding.swipeRefresh.isRefreshing = state is Resource.Loading
            if (state is Resource.Success) {
                adapter.submitList(state.data)
                binding.textEmpty.visibility = if (state.data.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        viewModel.load()
    }

    private fun showReceiptDialog(row: CobroRow) {
        val payment = row.payment ?: return
        val imageView = ImageView(this).apply {
            adjustViewBounds = true
            setPadding(32, 32, 32, 32)
        }
        Glide.with(this).load(payment.receiptUrl).into(imageView)

        AlertDialog.Builder(this)
            .setTitle(row.user.fullName)
            .setView(imageView)
            .setPositiveButton(R.string.cobros_approve) { _, _ -> viewModel.approve(payment.id) }
            .setNegativeButton(R.string.cobros_reject) { _, _ -> showRejectDialog(payment.id) }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    private fun showRejectDialog(paymentId: String) {
        val input = EditText(this).apply {
            hint = getString(R.string.cobros_reject_reason_hint)
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.cobros_reject)
            .setView(input)
            .setPositiveButton(R.string.cobros_reject) { _, _ ->
                viewModel.reject(paymentId, input.text?.toString().orEmpty())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
