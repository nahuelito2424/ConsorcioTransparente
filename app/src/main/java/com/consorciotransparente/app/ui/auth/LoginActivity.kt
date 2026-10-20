package com.consorciotransparente.app.ui.auth

import android.content.Intent
import android.os.Bundle
import com.consorciotransparente.app.ui.BaseActivity
import androidx.lifecycle.ViewModelProvider
import com.consorciotransparente.app.MainActivity
import com.consorciotransparente.app.data.repository.RepositoryProvider
import com.consorciotransparente.app.databinding.ActivityLoginBinding
import com.consorciotransparente.app.util.Resource

class LoginActivity : BaseActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by lazy {
        ViewModelProvider(this)[LoginViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Si ya hay una sesión activa, saltamos directo al dashboard.
        if (RepositoryProvider.auth.currentUid != null) {
            goToMain()
            return
        }

        binding.buttonLogin.setOnClickListener {
            viewModel.login(
                binding.inputEmail.text?.toString().orEmpty(),
                binding.inputPassword.text?.toString().orEmpty()
            )
        }

        binding.textGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        viewModel.state.observe(this) { state ->
            binding.progressBar.visibility = if (state is Resource.Loading) android.view.View.VISIBLE else android.view.View.GONE
            binding.buttonLogin.isEnabled = state !is Resource.Loading
            when (state) {
                is Resource.Error -> {
                    binding.textError.text = state.message
                    binding.textError.visibility = android.view.View.VISIBLE
                }
                is Resource.Success -> goToMain()
                else -> Unit
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
