package com.consorciotransparente.app.ui.proveedores

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.consorciotransparente.app.ui.BaseActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.consorciotransparente.app.databinding.ActivityProveedoresBinding
import com.consorciotransparente.app.util.Resource

/** HU-08: listado del directorio de proveedores, con alta sólo para el Administrador. */
class ProveedoresActivity : BaseActivity() {

    private lateinit var binding: ActivityProveedoresBinding
    private val viewModel: ProveedoresViewModel by lazy {
        ViewModelProvider(this)[ProveedoresViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProveedoresBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = ProviderAdapter { provider ->
            startActivity(
                Intent(this, ProviderDetailActivity::class.java)
                    .putExtra(ProviderDetailActivity.EXTRA_PROVIDER_ID, provider.id)
            )
        }
        binding.recyclerProviders.layoutManager = LinearLayoutManager(this)
        binding.recyclerProviders.adapter = adapter

        binding.buttonBack.setOnClickListener { finish() }
        binding.swipeRefresh.setOnRefreshListener { viewModel.load() }
        binding.buttonNewProvider.setOnClickListener {
            startActivity(Intent(this, AddEditProviderActivity::class.java))
        }

        viewModel.state.observe(this) { state ->
            binding.swipeRefresh.isRefreshing = state is Resource.Loading
            if (state is Resource.Success) {
                adapter.submitList(state.data.providers)
                binding.textEmpty.visibility = if (state.data.providers.isEmpty()) View.VISIBLE else View.GONE
                binding.buttonNewProvider.visibility = if (state.data.isAdmin) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }
}
