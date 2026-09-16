package com.consorciotransparente.app.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consorciotransparente.app.data.repository.AuthRepository
import com.consorciotransparente.app.util.Resource
import kotlinx.coroutines.launch

class LoginViewModel(private val repo: AuthRepository = AuthRepository()) : ViewModel() {

    private val _state = MutableLiveData<Resource<Unit>>()
    val state: LiveData<Resource<Unit>> = _state

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.value = Resource.Error("Completá tu correo y contraseña.")
            return
        }
        _state.value = Resource.Loading
        viewModelScope.launch {
            try {
                repo.login(email.trim(), password)
                _state.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _state.value = Resource.Error(e.message ?: "No se pudo iniciar sesión.")
            }
        }
    }
}
