package com.consorciotransparente.app.ui.votaciones

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consorciotransparente.app.data.model.VoteTopic
import com.consorciotransparente.app.data.repository.AuthRepository
import com.consorciotransparente.app.data.repository.RepositoryProvider
import com.consorciotransparente.app.data.repository.VoteRepository
import com.consorciotransparente.app.util.Resource
import kotlinx.coroutines.launch

data class VoteDetailUiState(
    val vote: VoteTopic,
    val isClosed: Boolean,
    val myOptionIndex: Int?, // null = todavía no votó
    val counts: IntArray?    // se completa sólo cuando corresponde mostrar resultados
)

class VoteDetailViewModel(
    private val voteRepo: VoteRepository = RepositoryProvider.vote,
    private val authRepo: AuthRepository = RepositoryProvider.auth
) : ViewModel() {

    private val _state = MutableLiveData<Resource<VoteDetailUiState>>()
    val state: LiveData<Resource<VoteDetailUiState>> = _state

    private val _submitState = MutableLiveData<Resource<Unit>>()
    val submitState: LiveData<Resource<Unit>> = _submitState

    fun load(buildingId: String, voteId: String) {
        _state.value = Resource.Loading
        viewModelScope.launch {
            try {
                val uid = authRepo.currentUid ?: error("Tenés que iniciar sesión de nuevo.")
                val vote = voteRepo.getVote(buildingId, voteId) ?: error("La votación ya no existe.")
                val myResponse = voteRepo.getMyResponse(buildingId, voteId, uid)
                val isClosed = voteRepo.isClosed(vote)
                val counts = if (isClosed || myResponse != null) {
                    voteRepo.getResultCounts(buildingId, voteId, vote.options.size)
                } else null

                _state.value = Resource.Success(
                    VoteDetailUiState(vote, isClosed, myResponse?.optionIndex, counts)
                )
            } catch (e: Exception) {
                _state.value = Resource.Error(e.message ?: "No se pudo cargar la votación.")
            }
        }
    }

    fun submitVote(buildingId: String, voteId: String, optionIndex: Int) {
        _submitState.value = Resource.Loading
        viewModelScope.launch {
            try {
                val uid = authRepo.currentUid ?: error("Tenés que iniciar sesión de nuevo.")
                voteRepo.submitVote(buildingId, voteId, uid, optionIndex)
                _submitState.value = Resource.Success(Unit)
                load(buildingId, voteId)
            } catch (e: Exception) {
                _submitState.value = Resource.Error(e.message ?: "No se pudo emitir el voto.")
            }
        }
    }
}
