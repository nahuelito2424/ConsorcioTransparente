package com.consorciotransparente.app.ui.tickets

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consorciotransparente.app.data.model.Role
import com.consorciotransparente.app.data.model.Ticket
import com.consorciotransparente.app.data.repository.AuthRepository
import com.consorciotransparente.app.data.repository.TicketRepository
import com.consorciotransparente.app.util.Resource
import kotlinx.coroutines.launch

data class TicketsUiState(val tickets: List<Ticket>, val buildingId: String, val authorName: String)

/** El Encargado/Administrador ven la cola completa; el Inquilino, sólo sus propios reportes. */
class TicketsViewModel(
    private val ticketRepo: TicketRepository = TicketRepository(),
    private val authRepo: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _state = MutableLiveData<Resource<TicketsUiState>>()
    val state: LiveData<Resource<TicketsUiState>> = _state

    fun load() {
        _state.value = Resource.Loading
        viewModelScope.launch {
            try {
                val user = authRepo.fetchCurrentUserProfile()
                val buildingId = user?.buildingId.orEmpty()
                if (user == null || buildingId.isBlank()) {
                    _state.value = Resource.Success(TicketsUiState(emptyList(), "", ""))
                    return@launch
                }
                val canSeeAll = user.roleEnum() == Role.ADMINISTRADOR || user.roleEnum() == Role.ENCARGADO
                val tickets = if (canSeeAll) ticketRepo.listTickets(buildingId) else ticketRepo.listMyTickets(buildingId, user.uid)
                _state.value = Resource.Success(TicketsUiState(tickets, buildingId, user.fullName))
            } catch (e: Exception) {
                _state.value = Resource.Error(e.message ?: "No se pudieron cargar los tickets.")
            }
        }
    }
}
