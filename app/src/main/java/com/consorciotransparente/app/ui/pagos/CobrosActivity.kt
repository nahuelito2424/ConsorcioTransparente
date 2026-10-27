package com.consorciotransparente.app.ui.pagos

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.consorciotransparente.app.R
import com.consorciotransparente.app.databinding.ActivityCobrosBinding
import com.consorciotransparente.app.ui.BaseActivity
import com.consorciotransparente.app.util.Resource
import java.text.NumberFormat
import java.util.Locale

/** HU-03: el Administrador ve el estado de cobro del mes y aprueba/rechaza comprobantes. */
class CobrosActivity : BaseActivity() {
    private lateinit var binding: ActivityCobrosBinding
    private val viewModel: CobrosViewModel by lazy { ViewModelProvider(this)[CobrosViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCobrosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonBack.setOnClickListener { finish() }

        val adapter = CobroAdapter(
            onRowClick = { row -> showReceiptDialog(row) },
            onVerHistorial = { row ->
                startActivity(
                    Intent(this, HistorialPagosActivity::class.java)
                        .putExtra("uid", row.user.uid)
                        .putExtra("userName", row.user.fullName)
                )
            }
        )
        binding.recyclerCobros.layoutManager = LinearLayoutManager(this)
        binding.recyclerCobros.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { viewModel.load() }

        viewModel.state.observe(this) { state ->
            binding.swipeRefresh.isRefreshing = state is Resource.Loading
            when (state) {
                is Resource.Success -> {
                    adapter.submitList(state.data)
                    binding.textEmpty.visibility = if (state.data.isEmpty()) View.VISIBLE else View.GONE
                }
                is Resource.Error -> Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                else -> Unit
            }
        }
        viewModel.actionState.observe(this) { state ->
            if (state is Resource.Error) {
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.load()
    }

    private fun showReceiptDialog(row: CobroRow) {
        val payment = row.payment ?: return
        val currencyFormat = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("es").setRegion("AR").build())
        val expectedAmount = row.user.rentAmount + row.user.expensesAmount

        // El monto declarado en el comprobante queda congelado al subirlo (HU-03); si el
        // Admin cambió el monto asignado al vecino después, se lo muestra aparte para que
        // no apruebe un comprobante por un monto viejo sin darse cuenta.
        val amountText = TextView(this).apply {
            text = if (payment.amount == expectedAmount) {
                getString(R.string.cobros_dialog_amount, currencyFormat.format(payment.amount))
            } else {
                getString(
                    R.string.cobros_dialog_amount_mismatch,
                    currencyFormat.format(payment.amount),
                    currencyFormat.format(expectedAmount)
                )
            }
            setPadding(0, 0, 0, 24)
        }
        val imageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(240))
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        Glide.with(this).load(payment.receiptUrl).into(imageView)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dpToPx(16)
            setPadding(padding, padding, padding, padding)
            addView(amountText)
            addView(imageView)
        }

        AlertDialog.Builder(this)
            .setTitle(row.user.fullName)
            .setView(container)
            .setPositiveButton(R.string.cobros_approve) { _, _ -> viewModel.approve(payment.id) }
            .setNegativeButton(R.string.cobros_reject) { _, _ -> showRejectDialog(payment.id) }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

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
