package com.consorciotransparente.app.ui.tickets

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consorciotransparente.app.data.model.Role
import com.consorciotransparente.app.data.model.Ticket
import com.consorciotransparente.app.data.repository.AuthRepository
import com.consorciotransparente.app.data.repository.RepositoryProvider
import com.consorciotransparente.app.data.repository.TicketRepository
import com.consorciotransparente.app.util.Resource
import kotlinx.coroutines.launch

data class TicketDetailUiState(val ticket: Ticket, val uid: String, val canManage: Boolean)

class TicketDetailViewModel(
    private val ticketRepo: TicketRepository = RepositoryProvider.ticket,
    private val authRepo: AuthRepository = RepositoryProvider.auth
) : ViewModel() {

    private val _state = MutableLiveData<Resource<TicketDetailUiState>>()
    val state: LiveData<Resource<TicketDetailUiState>> = _state

    private val _actionState = MutableLiveData<Resource<Unit>>()
    val actionState: LiveData<Resource<Unit>> = _actionState

    private val _mercadoPagoUrl = MutableLiveData<Resource<String>>()
    val mercadoPagoUrl: LiveData<Resource<String>> = _mercadoPagoUrl

    fun load(buildingId: String, ticketId: String) {
        _state.value = Resource.Loading
        viewModelScope.launch {
            try {
                val user = authRepo.fetchCurrentUserProfile() ?: error("Tenés que iniciar sesión de nuevo.")
                val ticket = ticketRepo.getTicket(buildingId, ticketId) ?: error("El ticket ya no existe.")
                val canManage = user.roleEnum() == Role.ADMINISTRADOR || user.roleEnum() == Role.ENCARGADO
                _state.value = Resource.Success(TicketDetailUiState(ticket, user.uid, canManage))
            } catch (e: Exception) {
                _state.value = Resource.Error(e.message ?: "No se pudo cargar el ticket.")
            }
        }
    }

    fun saveBudget(buildingId: String, ticketId: String, amount: Double) {
        _actionState.value = Resource.Loading
        viewModelScope.launch {
            try {
                ticketRepo.setBudget(buildingId, ticketId, amount)
                _actionState.value = Resource.Success(Unit)
                load(buildingId, ticketId)
            } catch (e: Exception) {
                _actionState.value = Resource.Error(e.message ?: "No se pudo guardar el presupuesto.")
            }
        }
    }

    fun confirmPurchase(buildingId: String, ticketId: String, amount: Double, receipt: Uri) {
        _actionState.value = Resource.Loading
        viewModelScope.launch {
            try {
                ticketRepo.uploadPurchaseReceipt(buildingId, ticketId, amount, receipt)
                _actionState.value = Resource.Success(Unit)
                load(buildingId, ticketId)
            } catch (e: Exception) {
                _actionState.value = Resource.Error(e.message ?: "No se pudo confirmar la compra.")
            }
        }
    }

    fun markPaid(buildingId: String, ticketId: String, receipt: Uri) {
        _actionState.value = Resource.Loading
        viewModelScope.launch {
            try {
                ticketRepo.markPaid(buildingId, ticketId, receipt)
                _actionState.value = Resource.Success(Unit)
                load(buildingId, ticketId)
            } catch (e: Exception) {
                _actionState.value = Resource.Error(e.message ?: "No se pudo confirmar el pago.")
            }
        }
    }

    fun payWithMercadoPago(buildingId: String, ticketId: String) {
        _mercadoPagoUrl.value = Resource.Loading
        viewModelScope.launch {
            try {
                val url = ticketRepo.createTicketMercadoPagoPreference(buildingId, ticketId)
                _mercadoPagoUrl.value = Resource.Success(url)
                load(buildingId, ticketId)
            } catch (e: Exception) {
                _mercadoPagoUrl.value = Resource.Error(e.message ?: "No se pudo iniciar el pago con Mercado Pago.")
            }
        }
    }
}
