package com.consorciotransparente.app.ui.proveedores

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consorciotransparente.app.data.model.Provider
import com.consorciotransparente.app.data.model.Role
import com.consorciotransparente.app.data.repository.AuthRepository
import com.consorciotransparente.app.data.repository.ProviderRepository
import com.consorciotransparente.app.data.repository.RepositoryProvider
import com.consorciotransparente.app.util.Resource
import kotlinx.coroutines.launch

data class ProveedoresUiState(val providers: List<Provider>, val isAdmin: Boolean)

/** HU-08: listado del directorio global de proveedores. */
class ProveedoresViewModel(
    private val providerRepo: ProviderRepository = RepositoryProvider.provider,
    private val authRepo: AuthRepository = RepositoryProvider.auth
) : ViewModel() {

    private val _state = MutableLiveData<Resource<ProveedoresUiState>>()
    val state: LiveData<Resource<ProveedoresUiState>> = _state

    fun load() {
        _state.value = Resource.Loading
        viewModelScope.launch {
            try {
                val user = authRepo.fetchCurrentUserProfile()
                val providers = providerRepo.listProviders().sortedBy { it.name }
                _state.value = Resource.Success(ProveedoresUiState(providers, user?.roleEnum() == Role.ADMINISTRADOR))
            } catch (e: Exception) {
                _state.value = Resource.Error(e.message ?: "No se pudo cargar el directorio de proveedores.")
            }
        }
    }
}
