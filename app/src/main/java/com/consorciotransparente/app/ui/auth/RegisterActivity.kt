package com.consorciotransparente.app.ui.auth

import android.content.Intent
import android.os.Bundle
import com.consorciotransparente.app.ui.BaseActivity
import androidx.lifecycle.ViewModelProvider
import com.consorciotransparente.app.MainActivity
import com.consorciotransparente.app.databinding.ActivityRegisterBinding
import com.consorciotransparente.app.util.Resource

class RegisterActivity : BaseActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: RegisterViewModel by lazy {
        ViewModelProvider(this)[RegisterViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonRegister.setOnClickListener {
            viewModel.register(
                binding.inputName.text?.toString().orEmpty(),
                binding.inputEmail.text?.toString().orEmpty(),
                binding.inputPassword.text?.toString().orEmpty(),
                binding.inputBuildingCode.text?.toString().orEmpty()
            )
        }

        binding.buttonBack.setOnClickListener { finish() }
        binding.textGoLogin.setOnClickListener { finish() }

        viewModel.state.observe(this) { state ->
            binding.progressBar.visibility = if (state is Resource.Loading) android.view.View.VISIBLE else android.view.View.GONE
            binding.buttonRegister.isEnabled = state !is Resource.Loading
            when (state) {
                is Resource.Error -> {
                    binding.textError.text = state.message
                    binding.textError.visibility = android.view.View.VISIBLE
                }
                is Resource.Success -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                else -> Unit
            }
        }
    }
}
