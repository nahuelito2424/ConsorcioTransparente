package com.consorciotransparente.app.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consorciotransparente.app.data.repository.AuthRepository
import com.consorciotransparente.app.data.repository.RepositoryProvider
import com.consorciotransparente.app.util.Resource
import kotlinx.coroutines.launch

class RegisterViewModel(private val repo: AuthRepository = RepositoryProvider.auth) : ViewModel() {

    private val _state = MutableLiveData<Resource<Unit>>()
    val state: LiveData<Resource<Unit>> = _state

    fun register(fullName: String, email: String, password: String, buildingCode: String) {
        if (fullName.isBlank() || email.isBlank() || password.length < 6) {
            _state.value = Resource.Error("Completá tu nombre, correo y una contraseña de al menos 6 caracteres.")
            return
        }
        _state.value = Resource.Loading
        viewModelScope.launch {
            try {
                repo.register(fullName.trim(), email.trim(), password, buildingCode.trim())
                _state.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _state.value = Resource.Error(e.message ?: "No se pudo crear la cuenta.")
            }
        }
    }
}
