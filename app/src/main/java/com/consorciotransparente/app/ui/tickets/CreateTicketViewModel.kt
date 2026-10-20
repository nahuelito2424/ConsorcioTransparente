package com.consorciotransparente.app.ui.tickets

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consorciotransparente.app.data.repository.AuthRepository
import com.consorciotransparente.app.data.repository.RepositoryProvider
import com.consorciotransparente.app.data.repository.TicketRepository
import com.consorciotransparente.app.util.Resource
import kotlinx.coroutines.launch

/** HU-10 Escenario 1: reporte con categoría, descripción y foto opcional. */
class CreateTicketViewModel(
    private val ticketRepo: TicketRepository = RepositoryProvider.ticket,
    private val authRepo: AuthRepository = RepositoryProvider.auth
) : ViewModel() {

    private val _state = MutableLiveData<Resource<Unit>>()
    val state: LiveData<Resource<Unit>> = _state

    fun report(category: String, description: String, photo: Uri?) {
        if (category.isBlank() || description.isBlank()) {
            _state.value = Resource.Error("Completá la categoría y la descripción del problema.")
            return
        }
        _state.value = Resource.Loading
        viewModelScope.launch {
            try {
                val user = authRepo.fetchCurrentUserProfile() ?: error("No se encontró tu perfil.")
                if (user.buildingId.isBlank()) error("Tu usuario todavía no está asignado a un edificio.")
                ticketRepo.createTicket(user.buildingId, category.trim(), description.trim(), user.uid, photo)
                _state.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _state.value = Resource.Error(e.message ?: "No se pudo reportar el problema.")
            }
        }
    }
}
