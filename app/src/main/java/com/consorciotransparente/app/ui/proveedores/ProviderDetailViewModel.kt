package com.consorciotransparente.app.ui.proveedores

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consorciotransparente.app.data.model.Provider
import com.consorciotransparente.app.data.model.ProviderReview
import com.consorciotransparente.app.data.model.Role
import com.consorciotransparente.app.data.repository.AuthRepository
import com.consorciotransparente.app.data.repository.ProviderRepository
import com.consorciotransparente.app.data.repository.RepositoryProvider
import com.consorciotransparente.app.util.Resource
import kotlinx.coroutines.launch

data class ProviderDetailUiState(
    val provider: Provider,
    val reviews: List<ProviderReview>,
    val averageStars: Double,
    val isAdmin: Boolean
)

/**
 * HU-08: ficha de un proveedor. El promedio de estrellas se calcula acá, a partir de
 * las reseñas cargadas (ver ProviderRepository) en vez de leerlo de
 * Provider.averageRating/ratingCount.
 */
class ProviderDetailViewModel(
    private val providerRepo: ProviderRepository = RepositoryProvider.provider,
    private val authRepo: AuthRepository = RepositoryProvider.auth
) : ViewModel() {

    private val _state = MutableLiveData<Resource<ProviderDetailUiState>>()
    val state: LiveData<Resource<ProviderDetailUiState>> = _state

    private val _reviewState = MutableLiveData<Resource<Unit>>()
    val reviewState: LiveData<Resource<Unit>> = _reviewState

    private val _deleteState = MutableLiveData<Resource<Unit>>()
    val deleteState: LiveData<Resource<Unit>> = _deleteState

    private var providerId = ""

    fun load(providerId: String) {
        this.providerId = providerId
        _state.value = Resource.Loading
        viewModelScope.launch {
            try {
                val provider = providerRepo.getProvider(providerId) ?: error("No se encontró el proveedor.")
                val reviews = providerRepo.listReviews(providerId)
                val average = if (reviews.isEmpty()) 0.0 else reviews.map { it.stars }.average()
                val user = authRepo.fetchCurrentUserProfile()
                _state.value = Resource.Success(
                    ProviderDetailUiState(provider, reviews, average, user?.roleEnum() == Role.ADMINISTRADOR)
                )
            } catch (e: Exception) {
                _state.value = Resource.Error(e.message ?: "No se pudo cargar el proveedor.")
            }
        }
    }

    fun addReview(stars: Int, comment: String) {
        _reviewState.value = Resource.Loading
        viewModelScope.launch {
            try {
                val uid = authRepo.currentUid ?: error("Tenés que iniciar sesión de nuevo.")
                providerRepo.addReview(providerId, ProviderReview(authorUid = uid, comment = comment, stars = stars))
                _reviewState.value = Resource.Success(Unit)
                load(providerId)
            } catch (e: Exception) {
                _reviewState.value = Resource.Error(e.message ?: "No se pudo guardar la reseña.")
            }
        }
    }

    fun delete() {
        _deleteState.value = Resource.Loading
        viewModelScope.launch {
            try {
                providerRepo.deleteProvider(providerId)
                _deleteState.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _deleteState.value = Resource.Error(e.message ?: "No se pudo eliminar el proveedor.")
            }
        }
    }
}
