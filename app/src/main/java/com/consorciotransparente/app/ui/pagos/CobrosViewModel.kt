package com.consorciotransparente.app.ui.pagos

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consorciotransparente.app.data.model.Payment
import com.consorciotransparente.app.data.model.User
import com.consorciotransparente.app.data.repository.AuthRepository
import com.consorciotransparente.app.data.repository.PaymentRepository
import com.consorciotransparente.app.data.repository.RepositoryProvider
import com.consorciotransparente.app.data.repository.ResidentRepository
import com.consorciotransparente.app.util.Resource
import com.consorciotransparente.app.util.currentPeriod
import kotlinx.coroutines.launch

data class CobroRow(val user: User, val payment: Payment?)

/** HU-03: panel del Administrador con el estado de cobro de este mes por vecino. */
class CobrosViewModel(
    private val authRepo: AuthRepository = RepositoryProvider.auth,
    private val residentRepo: ResidentRepository = RepositoryProvider.resident,
    private val paymentRepo: PaymentRepository = RepositoryProvider.payment
) : ViewModel() {
    private val _state = MutableLiveData<Resource<List<CobroRow>>>()
    val state: LiveData<Resource<List<CobroRow>>> = _state

    private val _actionState = MutableLiveData<Resource<Unit>>()
    val actionState: LiveData<Resource<Unit>> = _actionState

    private var buildingId = ""
    private val period = currentPeriod()

    fun load() {
        _state.value = Resource.Loading
        viewModelScope.launch {
            try {
                val admin = authRepo.fetchCurrentUserProfile() ?: error("No se encontró tu perfil.")
                buildingId = admin.buildingId
                val residents = residentRepo.listResidents(buildingId)
                val payments = paymentRepo.listPaymentsForBuilding(buildingId, period)
                val rows = residents.map { resident ->
                    CobroRow(resident, payments.firstOrNull { it.uid == resident.uid })
                }
                _state.value = Resource.Success(rows)
            } catch (e: Exception) {
                _state.value = Resource.Error(e.message ?: "No se pudo cargar el panel de cobros.")
            }
        }
    }

    fun approve(paymentId: String) {
        _actionState.value = Resource.Loading
        viewModelScope.launch {
            try {
                val adminUid = authRepo.currentUid ?: error("Tenés que iniciar sesión de nuevo.")
                paymentRepo.approvePayment(buildingId, paymentId, adminUid)
                _actionState.value = Resource.Success(Unit)
                load()
            } catch (e: Exception) {
                _actionState.value = Resource.Error(e.message ?: "No se pudo aprobar el pago.")
            }
        }
    }

    fun reject(paymentId: String, reason: String) {
        _actionState.value = Resource.Loading
        viewModelScope.launch {
            try {
                paymentRepo.rejectPayment(buildingId, paymentId, reason)
                _actionState.value = Resource.Success(Unit)
                load()
            } catch (e: Exception) {
                _actionState.value = Resource.Error(e.message ?: "No se pudo rechazar el pago.")
            }
        }
    }
}
