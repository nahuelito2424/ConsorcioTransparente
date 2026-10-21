package com.consorciotransparente.app.ui.vecinos

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.consorciotransparente.app.R
import com.consorciotransparente.app.databinding.ActivityVecinosBinding
import com.consorciotransparente.app.ui.BaseActivity
import com.consorciotransparente.app.util.Resource

/** HU-03: el Administrador asigna un monto fijo de alquiler/expensas a cada vecino. */
class VecinosActivity : BaseActivity() {
    private lateinit var binding: ActivityVecinosBinding
    private val viewModel: VecinosViewModel by lazy { ViewModelProvider(this)[VecinosViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVecinosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonBack.setOnClickListener { finish() }

        val adapter = VecinoAdapter { uid, rentAmount, expensesAmount ->
            viewModel.saveAmounts(uid, rentAmount, expensesAmount)
        }
        binding.recyclerVecinos.layoutManager = LinearLayoutManager(this)
        binding.recyclerVecinos.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { viewModel.load() }

        viewModel.state.observe(this) { state ->
            binding.swipeRefresh.isRefreshing = state is Resource.Loading
            if (state is Resource.Success) {
                adapter.submitList(state.data)
                binding.textEmpty.visibility = if (state.data.isEmpty()) View.VISIBLE else View.GONE
            }
        }
        viewModel.saveState.observe(this) { state ->
            if (state is Resource.Success) {
                Toast.makeText(this, R.string.vecinos_saved, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.load()
    }
}
