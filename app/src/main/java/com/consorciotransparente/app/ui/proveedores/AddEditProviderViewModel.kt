package com.consorciotransparente.app.ui.proveedores

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consorciotransparente.app.data.model.Provider
import com.consorciotransparente.app.data.repository.ProviderRepository
import com.consorciotransparente.app.data.repository.RepositoryProvider
import com.consorciotransparente.app.util.Resource
import kotlinx.coroutines.launch

data class ProviderLoadState(val provider: Provider?, val isNew: Boolean)

/** HU-08: alta/edición de un proveedor (sólo Administrador, reforzado en firestore.rules). */
class AddEditProviderViewModel(
    private val providerRepo: ProviderRepository = RepositoryProvider.provider
) : ViewModel() {

    private val _loadState = MutableLiveData<Resource<ProviderLoadState>>()
    val loadState: LiveData<Resource<ProviderLoadState>> = _loadState

    private val _saveState = MutableLiveData<Resource<Unit>>()
    val saveState: LiveData<Resource<Unit>> = _saveState

    fun load(providerId: String) {
        if (providerId.isBlank()) {
            _loadState.value = Resource.Success(ProviderLoadState(null, true))
            return
        }
        _loadState.value = Resource.Loading
        viewModelScope.launch {
            try {
                val provider = providerRepo.getProvider(providerId)
                _loadState.value = Resource.Success(ProviderLoadState(provider, false))
            } catch (e: Exception) {
                _loadState.value = Resource.Error(e.message ?: "No se pudo cargar el proveedor.")
            }
        }
    }

    fun save(provider: Provider, isNew: Boolean) {
        _saveState.value = Resource.Loading
        viewModelScope.launch {
            try {
                if (isNew) providerRepo.createProvider(provider) else providerRepo.updateProvider(provider)
                _saveState.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _saveState.value = Resource.Error(e.message ?: "No se pudo guardar el proveedor.")
            }
        }
    }
}
