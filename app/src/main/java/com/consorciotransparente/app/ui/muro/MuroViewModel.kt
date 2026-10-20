package com.consorciotransparente.app.ui.muro

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consorciotransparente.app.data.model.Post
import com.consorciotransparente.app.data.model.Role
import com.consorciotransparente.app.data.repository.AuthRepository
import com.consorciotransparente.app.data.repository.MuroRepository
import com.consorciotransparente.app.data.repository.RepositoryProvider
import com.consorciotransparente.app.util.Resource
import kotlinx.coroutines.launch

data class MuroUiState(
    val posts: List<Post>,
    val isAdmin: Boolean,
    val pendingCount: Int,
    val buildingId: String,
    val authorName: String
)

class MuroViewModel(
    private val muroRepo: MuroRepository = RepositoryProvider.muro,
    private val authRepo: AuthRepository = RepositoryProvider.auth
) : ViewModel() {

    private val _state = MutableLiveData<Resource<MuroUiState>>()
    val state: LiveData<Resource<MuroUiState>> = _state

    fun load() {
        _state.value = Resource.Loading
        viewModelScope.launch {
            try {
                val user = authRepo.fetchCurrentUserProfile()
                val buildingId = user?.buildingId.orEmpty()
                if (user == null || buildingId.isBlank()) {
                    _state.value = Resource.Success(MuroUiState(emptyList(), false, 0, "", ""))
                    return@launch
                }
                val isAdmin = user.roleEnum() == Role.ADMINISTRADOR
                val posts = muroRepo.listApprovedPosts(buildingId)
                val pendingCount = if (isAdmin) muroRepo.listPendingPosts(buildingId).size else 0
                _state.value = Resource.Success(
                    MuroUiState(posts, isAdmin, pendingCount, buildingId, user.fullName)
                )
            } catch (e: Exception) {
                _state.value = Resource.Error(e.message ?: "No se pudo cargar el muro.")
            }
        }
    }
}
