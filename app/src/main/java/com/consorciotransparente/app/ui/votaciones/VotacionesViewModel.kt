package com.consorciotransparente.app.ui.votaciones

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consorciotransparente.app.data.model.Role
import com.consorciotransparente.app.data.model.VoteTopic
import com.consorciotransparente.app.data.repository.AuthRepository
import com.consorciotransparente.app.data.repository.VoteRepository
import com.consorciotransparente.app.util.Resource
import kotlinx.coroutines.launch

data class VoteListItem(
    val vote: VoteTopic,
    val voteCount: Int,
    val hasVoted: Boolean,
    val isClosed: Boolean
)

data class VotacionesUiState(val items: List<VoteListItem>, val isAdmin: Boolean, val buildingId: String)

class VotacionesViewModel(
    private val voteRepo: VoteRepository = VoteRepository(),
    private val authRepo: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _state = MutableLiveData<Resource<VotacionesUiState>>()
    val state: LiveData<Resource<VotacionesUiState>> = _state

    fun load() {
        _state.value = Resource.Loading
        viewModelScope.launch {
            try {
                val user = authRepo.fetchCurrentUserProfile()
                val buildingId = user?.buildingId.orEmpty()
                if (user == null || buildingId.isBlank()) {
                    _state.value = Resource.Success(VotacionesUiState(emptyList(), false, ""))
                    return@launch
                }
                val votes = voteRepo.listVotes(buildingId)
                val items = votes.map { vote ->
                    VoteListItem(
                        vote = vote,
                        voteCount = voteRepo.countVotes(buildingId, vote.id),
                        hasVoted = voteRepo.getMyResponse(buildingId, vote.id, user.uid) != null,
                        isClosed = voteRepo.isClosed(vote)
                    )
                }
                _state.value = Resource.Success(
                    VotacionesUiState(items, user.roleEnum() == Role.ADMINISTRADOR, buildingId)
                )
            } catch (e: Exception) {
                _state.value = Resource.Error(e.message ?: "No se pudieron cargar las votaciones.")
            }
        }
    }
}
