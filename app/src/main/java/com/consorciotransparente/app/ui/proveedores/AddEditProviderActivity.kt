package com.consorciotransparente.app.ui.proveedores

import android.os.Bundle
import android.view.View
import com.consorciotransparente.app.ui.BaseActivity
import androidx.lifecycle.ViewModelProvider
import com.consorciotransparente.app.R
import com.consorciotransparente.app.data.model.Provider
import com.consorciotransparente.app.databinding.ActivityAddEditProviderBinding
import com.consorciotransparente.app.util.Resource

/** HU-08: alta/edición de un proveedor (sólo Administrador, ver MasFragment/ProveedoresActivity). */
class AddEditProviderActivity : BaseActivity() {

    companion object {
        const val EXTRA_PROVIDER_ID = "providerId"
    }

    private lateinit var binding: ActivityAddEditProviderBinding
    private val viewModel: AddEditProviderViewModel by lazy {
        ViewModelProvider(this)[AddEditProviderViewModel::class.java]
    }
    private var isNew = true
    private var existingProviderId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditProviderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val providerId = intent.getStringExtra(EXTRA_PROVIDER_ID).orEmpty()

        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonSave.setOnClickListener { onSaveClicked() }

        viewModel.loadState.observe(this) { state ->
            binding.progressBar.visibility = if (state is Resource.Loading) View.VISIBLE else View.GONE
            when (state) {
                is Resource.Success -> renderLoaded(state.data)
                is Resource.Error -> showError(state.message)
                else -> Unit
            }
        }
        viewModel.saveState.observe(this) { state ->
            binding.progressBar.visibility = if (state is Resource.Loading) View.VISIBLE else View.GONE
            binding.buttonSave.isEnabled = state !is Resource.Loading
            when (state) {
                is Resource.Error -> showError(state.message)
                is Resource.Success -> finish()
                else -> Unit
            }
        }

        viewModel.load(providerId)
    }

    private fun renderLoaded(data: ProviderLoadState) {
        isNew = data.isNew
        val provider = data.provider ?: Provider()
        existingProviderId = provider.id

        binding.textTitle.text = getString(
            if (isNew) R.string.providers_title_new else R.string.providers_title_edit
        )
        binding.inputName.setText(provider.name)
        binding.inputCategory.setText(provider.category)
        binding.inputPhone.setText(provider.phone)
    }

    private fun onSaveClicked() {
        val provider = Provider(
            id = existingProviderId,
            name = binding.inputName.text?.toString()?.trim().orEmpty(),
            category = binding.inputCategory.text?.toString()?.trim().orEmpty(),
            phone = binding.inputPhone.text?.toString()?.trim().orEmpty()
        )

        if (provider.name.isBlank() || provider.category.isBlank() || provider.phone.isBlank()) {
            showError(getString(R.string.providers_fields_required))
            return
        }

        viewModel.save(provider, isNew)
    }

    private fun showError(message: String) {
        binding.textError.text = message
        binding.textError.visibility = View.VISIBLE
    }
}
