package com.consorciotransparente.app.ui.pagos

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.consorciotransparente.app.R
import com.consorciotransparente.app.data.model.Payment
import com.consorciotransparente.app.data.repository.RepositoryProvider
import com.consorciotransparente.app.databinding.ActivityHistorialPagosBinding
import com.consorciotransparente.app.ui.BaseActivity
import com.consorciotransparente.app.util.Resource
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Backlog "Estado de cuenta por unidad" — ver design doc en docs/superpowers/specs. */
class HistorialPagosActivity : BaseActivity() {
    private lateinit var binding: ActivityHistorialPagosBinding
    private val viewModel: HistorialPagosViewModel by lazy {
        ViewModelProvider(this)[HistorialPagosViewModel::class.java]
    }
    private val esAr = Locale.Builder().setLanguage("es").setRegion("AR").build()
    private val monthLabelFormat = SimpleDateFormat("MMMM yyyy", esAr)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistorialPagosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uid = intent.getStringExtra("uid").orEmpty()
        val userName = intent.getStringExtra("userName").orEmpty()

        binding.textTitle.text = if (uid == RepositoryProvider.auth.currentUid) {
            getString(R.string.historial_title_self)
        } else {
            getString(R.string.historial_title_other, userName)
        }

        val adapter = HistorialPagosAdapter(onVerComprobante = { payment -> showReceiptDialog(payment) })
        binding.recyclerHistorial.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistorial.adapter = adapter

        binding.buttonBack.setOnClickListener { finish() }

        viewModel.state.observe(this) { state ->
            binding.progressBar.visibility = if (state is Resource.Loading) View.VISIBLE else View.GONE
            binding.textError.visibility = View.GONE
            when (state) {
                is Resource.Success -> adapter.submitList(state.data)
                is Resource.Error -> {
                    binding.textError.text = state.message
                    binding.textError.visibility = View.VISIBLE
                }
                else -> Unit
            }
        }

        viewModel.load(uid)
    }

    private fun showReceiptDialog(payment: Payment) {
        val (year, month) = payment.period.split("-").map { it.toInt() }
        val date = Calendar.getInstance().apply { set(year, month - 1, 1) }.time
        val periodLabel = monthLabelFormat.format(date).replaceFirstChar { it.uppercase() }

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
            addView(imageView)
        }

        AlertDialog.Builder(this)
            .setTitle(periodLabel)
            .setView(container)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
