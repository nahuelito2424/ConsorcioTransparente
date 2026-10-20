package com.consorciotransparente.app.ui.muro

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import com.consorciotransparente.app.ui.BaseActivity
import androidx.lifecycle.ViewModelProvider
import com.consorciotransparente.app.databinding.ActivityCreatePostBinding
import com.consorciotransparente.app.util.Resource

/** HU-05 Escenario 1: cualquier usuario puede proponer una publicación (queda pendiente de aprobación). */
class CreatePostActivity : BaseActivity() {

    companion object {
        const val EXTRA_BUILDING_ID = "buildingId"
        const val EXTRA_AUTHOR_NAME = "authorName"
    }

    private lateinit var binding: ActivityCreatePostBinding
    private val viewModel: CreatePostViewModel by lazy {
        ViewModelProvider(this)[CreatePostViewModel::class.java]
    }
    private var imageUri: Uri? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            imageUri = uri
            binding.imagePreview.setImageURI(uri)
            binding.imagePreview.visibility = View.VISIBLE
            binding.buttonAttachImage.text = "Cambiar imagen"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreatePostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonAttachImage.setOnClickListener { pickImage.launch("image/*") }

        binding.buttonPublish.setOnClickListener {
            val buildingId = intent.getStringExtra(EXTRA_BUILDING_ID).orEmpty()
            val authorName = intent.getStringExtra(EXTRA_AUTHOR_NAME).orEmpty()
            viewModel.publish(
                buildingId = buildingId,
                title = binding.inputTitle.text?.toString().orEmpty(),
                body = binding.inputBody.text?.toString().orEmpty(),
                authorName = authorName,
                image = imageUri
            )
        }

        viewModel.state.observe(this) { state ->
            binding.progressBar.visibility = if (state is Resource.Loading) View.VISIBLE else View.GONE
            binding.buttonPublish.isEnabled = state !is Resource.Loading
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
