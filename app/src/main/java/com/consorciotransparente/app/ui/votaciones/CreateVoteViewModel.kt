package com.consorciotransparente.app.ui.votaciones

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consorciotransparente.app.data.repository.AuthRepository
import com.consorciotransparente.app.data.repository.VoteRepository
import com.consorciotransparente.app.util.Resource
import kotlinx.coroutines.launch
import java.util.Date

class CreateVoteViewModel(
    private val voteRepo: VoteRepository = VoteRepository(),
    private val authRepo: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _state = MutableLiveData<Resource<Unit>>()
    val state: LiveData<Resource<Unit>> = _state

    /** HU-02 Escenario 4: no se puede publicar una votación con menos de 2 opciones. */
    fun create(buildingId: String, title: String, description: String, options: List<String>, closesAt: Date?) {
        val cleanOptions = options.map { it.trim() }.filter { it.isNotBlank() }
        if (title.isBlank()) {
            _state.value = Resource.Error("Escribí el tema de la votación.")
            return
        }
        if (cleanOptions.size < 2) {
            _state.value = Resource.Error("Una votación necesita al menos 2 opciones.")
            return
        }
        if (closesAt == null) {
            _state.value = Resource.Error("Elegí una fecha de cierre.")
            return
        }
        _state.value = Resource.Loading
        viewModelScope.launch {
            try {
                val uid = authRepo.currentUid ?: error("Tenés que iniciar sesión de nuevo.")
                voteRepo.createVote(buildingId, title.trim(), description.trim(), cleanOptions, closesAt, uid)
                _state.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _state.value = Resource.Error(e.message ?: "No se pudo crear la votación.")
            }
        }
    }
}
