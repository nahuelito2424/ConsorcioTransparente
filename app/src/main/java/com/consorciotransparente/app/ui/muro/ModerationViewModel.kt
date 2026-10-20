package com.consorciotransparente.app.ui.muro

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consorciotransparente.app.data.model.Post
import com.consorciotransparente.app.data.repository.MuroRepository
import com.consorciotransparente.app.data.repository.RepositoryProvider
import com.consorciotransparente.app.util.Resource
import kotlinx.coroutines.launch

/** Cola de moderación del Administrador (HU-05 Escenario 3). */
class ModerationViewModel(
    private val muroRepo: MuroRepository = RepositoryProvider.muro
) : ViewModel() {

    private val _state = MutableLiveData<Resource<List<Post>>>()
    val state: LiveData<Resource<List<Post>>> = _state

    fun load(buildingId: String) {
        _state.value = Resource.Loading
        viewModelScope.launch {
            try {
                _state.value = Resource.Success(muroRepo.listPendingPosts(buildingId))
            } catch (e: Exception) {
                _state.value = Resource.Error(e.message ?: "No se pudo cargar la moderación.")
            }
        }
    }

    fun approve(buildingId: String, postId: String) {
        viewModelScope.launch {
            try {
                muroRepo.approvePost(buildingId, postId)
                load(buildingId)
            } catch (e: Exception) {
                _state.value = Resource.Error(e.message ?: "No se pudo aprobar la publicación.")
            }
        }
    }

    fun reject(buildingId: String, postId: String) {
        viewModelScope.launch {
            try {
                muroRepo.rejectPost(buildingId, postId)
                load(buildingId)
            } catch (e: Exception) {
                _state.value = Resource.Error(e.message ?: "No se pudo rechazar la publicación.")
            }
        }
    }
}
