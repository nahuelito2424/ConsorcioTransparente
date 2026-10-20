package com.consorciotransparente.app.ui.muro

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consorciotransparente.app.data.repository.AuthRepository
import com.consorciotransparente.app.data.repository.MuroRepository
import com.consorciotransparente.app.data.repository.RepositoryProvider
import com.consorciotransparente.app.util.Resource
import kotlinx.coroutines.launch

class CreatePostViewModel(
    private val muroRepo: MuroRepository = RepositoryProvider.muro,
    private val authRepo: AuthRepository = RepositoryProvider.auth
) : ViewModel() {

    private val _state = MutableLiveData<Resource<Unit>>()
    val state: LiveData<Resource<Unit>> = _state

    fun publish(buildingId: String, title: String, body: String, authorName: String, image: Uri?) {
        if (title.isBlank() || body.isBlank()) {
            _state.value = Resource.Error("Completá el título y el texto de la publicación.")
            return
        }
        _state.value = Resource.Loading
        viewModelScope.launch {
            try {
                val uid = authRepo.currentUid ?: error("Tenés que iniciar sesión de nuevo.")
                muroRepo.createPost(buildingId, title.trim(), body.trim(), uid, authorName, image)
                _state.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _state.value = Resource.Error(e.message ?: "No se pudo enviar la publicación.")
            }
        }
    }
}
