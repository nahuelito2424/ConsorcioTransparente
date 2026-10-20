package com.consorciotransparente.app.ui.facturas

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import com.consorciotransparente.app.ui.BaseActivity
import androidx.lifecycle.ViewModelProvider
import com.consorciotransparente.app.databinding.ActivityAddExpenseBinding
import com.consorciotransparente.app.util.Resource

/** Alta de gasto (HU-01): el botón Guardar queda deshabilitado hasta adjuntar la foto. */
class AddExpenseActivity : BaseActivity() {

    private lateinit var binding: ActivityAddExpenseBinding
    private val viewModel: AddExpenseViewModel by lazy {
        ViewModelProvider(this)[AddExpenseViewModel::class.java]
    }
    private var receiptUri: Uri? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            receiptUri = uri
            binding.imagePreview.setImageURI(uri)
            binding.imagePreview.visibility = View.VISIBLE
            binding.textPhotoWarning.visibility = View.GONE
            binding.buttonAttachPhoto.text = "Cambiar foto"
            binding.buttonSave.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddExpenseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonAttachPhoto.setOnClickListener { pickImage.launch("image/*") }

        binding.buttonSave.setOnClickListener {
            val amount = binding.inputAmount.text?.toString()?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
            viewModel.save(
                description = binding.inputDescription.text?.toString().orEmpty(),
                category = binding.inputCategory.text?.toString().orEmpty(),
                providerName = binding.inputProvider.text?.toString().orEmpty(),
                amount = amount,
                receiptImage = receiptUri
            )
        }

        viewModel.state.observe(this) { state ->
            binding.progressBar.visibility = if (state is Resource.Loading) View.VISIBLE else View.GONE
            binding.buttonSave.isEnabled = state !is Resource.Loading && receiptUri != null
            when (state) {
                is Resource.Error -> {
                    binding.textError.text = state.message
                    binding.textError.visibility = View.VISIBLE
                }
                is Resource.Success -> finish()
                else -> Unit
            }
        }
    }
}
