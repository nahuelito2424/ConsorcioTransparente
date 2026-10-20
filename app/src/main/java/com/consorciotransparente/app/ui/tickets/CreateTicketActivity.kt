package com.consorciotransparente.app.ui.tickets

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import com.consorciotransparente.app.ui.BaseActivity
import androidx.lifecycle.ViewModelProvider
import com.consorciotransparente.app.databinding.ActivityCreateTicketBinding
import com.consorciotransparente.app.util.Resource

class CreateTicketActivity : BaseActivity() {

    private lateinit var binding: ActivityCreateTicketBinding
    private val viewModel: CreateTicketViewModel by lazy {
        ViewModelProvider(this)[CreateTicketViewModel::class.java]
    }
    private var photoUri: Uri? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            photoUri = uri
            binding.imagePreview.setImageURI(uri)
            binding.imagePreview.visibility = View.VISIBLE
            binding.buttonAttachPhoto.text = "Cambiar foto"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateTicketBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonAttachPhoto.setOnClickListener { pickImage.launch("image/*") }

        binding.buttonReport.setOnClickListener {
            viewModel.report(
                category = binding.inputCategory.text?.toString().orEmpty(),
                description = binding.inputDescription.text?.toString().orEmpty(),
                photo = photoUri
            )
        }

        viewModel.state.observe(this) { state ->
            binding.progressBar.visibility = if (state is Resource.Loading) View.VISIBLE else View.GONE
            binding.buttonReport.isEnabled = state !is Resource.Loading
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
